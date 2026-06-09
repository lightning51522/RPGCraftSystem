package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.attribute.GatherAttributeBatchEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 属性快照管理器 —— 三级性能防线
 * <p>
 * 为 {@link AttributeSnapshot} 的获取提供多级缓存，避免高频属性查询
 * （如 AOE 伤害场景）导致的重复管线计算。
 * <p>
 * <b>三级防线</b>：
 * <ol>
 *   <li><b>Tick 级缓存</b>（{@code tickCache}）：每 Tick 自动清空，
 *       同一 Tick 内多次访问同一实体直接命中，零计算开销</li>
 *   <li><b>批量事件优化</b>（{@link GatherAttributeBatchEvent}）：
 *       多个实体同时需要快照时，触发一次批量事件代替 N 次单实体事件</li>
 *   <li><b>跨 Tick 缓存</b>（{@code crossTickCache}）：
 *       缓存跨 Tick 有效，通过 {@code entity.tickCount} 校验时效性；
 *       配合 {@link #markDirty} 主动失效</li>
 * </ol>
 * <p>
 * <b>缓存一致性</b>：
 * <ul>
 *   <li>属性变更时调用 {@link #markDirty(LivingEntity)} 标记脏</li>
 *   <li>脏实体在下次 {@link #getSnapshot} 时自动重新计算</li>
 *   <li>{@link #tickCleanup(ServerLevel)} 在每 Tick 末尾清理脏标记集合</li>
 * </ul>
 * <p>
 * 此类为静态工具类（与 {@link AttributeManager} 模式一致），
 * 无需通过 {@code RPGSystems} 路由。
 * <p>
 * <b>线程安全</b>：所有方法应在服务端线程调用。
 *
 * @see MobSnapshotBuilder
 * @see GatherAttributeBatchEvent
 */
public final class AttributeSnapshotManager {

    /**
     * 跨 Tick 缓存最大容量
     * <p>
     * 超过此阈值时 {@link #tickCleanup} 会清空整个跨 Tick 缓存，
     * 防止大量实体场景下的内存泄漏。
     */
    private static final int MAX_CROSS_TICK_CACHE_SIZE = 512;

    // ====================================================================
    // 第一道防线：Tick 级缓存
    // Key: entity.getId()（int，比 UUID 快 3-5 倍）
    // Value: AttributeSnapshot
    // 生命周期：每 Tick 清空（通过 checkTickRotation）
    // ====================================================================

    private static final Int2ObjectMap<AttributeSnapshot> tickCache = new Int2ObjectOpenHashMap<>();
    private static long lastTickGameTime = -1;

    // ====================================================================
    // 第三道防线：跨 Tick 长效缓存
    // Key: entity.getId()
    // Value: CacheEntry(snapshot + entityTickCount)
    // 生命周期：跨 Tick 有效，通过 entityTickCount 校验时效性
    // ====================================================================

    private static final Int2ObjectMap<CacheEntry> crossTickCache = new Int2ObjectOpenHashMap<>();

    // ====================================================================
    // 脏标记集合
    // 标记为脏的实体在下次 getSnapshot 时强制重新计算
    // ====================================================================

    private static final IntSet dirtyEntities = new IntOpenHashSet();

    /**
     * 跨 Tick 缓存条目
     *
     * @param snapshot        属性快照
     * @param entityTickCount 计算快照时的 entity.tickCount（用于时效校验）
     */
    record CacheEntry(AttributeSnapshot snapshot, int entityTickCount) {
    }

    private AttributeSnapshotManager() {
    } // 禁止实例化

    // ====================================================================
    // Tick 轮转检测
    // ====================================================================

    /**
     * 检测 Tick 轮转，在新 Tick 开始时清空 Tick 级缓存
     * <p>
     * 通过 {@code level.getGameTime()} 检测 Tick 变化。
     * 在每次 {@link #getSnapshot} / {@link #getSnapshots} 开头调用。
     *
     * @param level 当前维度（用于获取游戏时间）
     */
    private static void checkTickRotation(Level level) {
        long gameTime = level.getGameTime();
        if (gameTime != lastTickGameTime) {
            tickCache.clear();
            lastTickGameTime = gameTime;
        }
    }

    // ====================================================================
    // 公开 API
    // ====================================================================

    /**
     * 获取单个实体的属性快照（带缓存）
     * <p>
     * 查找顺序：Tick 缓存 → 脏标记检查 → 跨 Tick 缓存 → 实时计算。
     *
     * @param entity 目标实体
     * @return 属性快照（不会返回 null）
     */
    public static AttributeSnapshot getSnapshot(LivingEntity entity) {
        checkTickRotation(entity.level());

        int entityId = entity.getId();

        // === 第一道防线：Tick 级缓存 ===
        AttributeSnapshot tickCached = tickCache.get(entityId);
        if (tickCached != null) {
            return tickCached;
        }

        // === 脏标记检查：脏实体跳过跨 Tick 缓存 ===
        if (dirtyEntities.remove(entityId)) {
            crossTickCache.remove(entityId);
        }

        // === 第三道防线：跨 Tick 缓存 ===
        CacheEntry entry = crossTickCache.get(entityId);
        if (entry != null && entry.entityTickCount() == entity.tickCount) {
            // 命中跨 Tick 缓存，同时存入 Tick 缓存加速后续访问
            tickCache.put(entityId, entry.snapshot());
            return entry.snapshot();
        }

        // === 缓存未命中：实时计算 ===
        AttributeSnapshot snapshot = computeSnapshot(entity);

        // 存入两级缓存
        tickCache.put(entityId, snapshot);
        crossTickCache.put(entityId, new CacheEntry(snapshot, entity.tickCount));

        return snapshot;
    }

    /**
     * 批量获取实体属性快照（AOE 优化）
     * <p>
     * 对多个实体同时获取快照时，利用 {@link GatherAttributeBatchEvent}
     * 一次性收集所有非玩家实体的动态修饰符，避免 N 次事件分发。
     * <p>
     * 返回列表与输入列表一一对应（相同顺序）。
     *
     * @param entities 目标实体列表
     * @return 属性快照列表（与输入一一对应，不含 null）
     */
    public static List<AttributeSnapshot> getSnapshots(List<LivingEntity> entities) {
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        checkTickRotation(entities.get(0).level());

        // 结果容器（按原始顺序填充）
        AttributeSnapshot[] results = new AttributeSnapshot[entities.size()];
        List<LivingEntity> uncached = new ArrayList<>();

        // === 第一遍：检查缓存 ===
        for (int i = 0; i < entities.size(); i++) {
            LivingEntity entity = entities.get(i);
            int entityId = entity.getId();

            // Tick 缓存
            AttributeSnapshot tickCached = tickCache.get(entityId);
            if (tickCached != null) {
                results[i] = tickCached;
                continue;
            }

            // 脏标记
            if (dirtyEntities.remove(entityId)) {
                crossTickCache.remove(entityId);
            }

            // 跨 Tick 缓存
            CacheEntry entry = crossTickCache.get(entityId);
            if (entry != null && entry.entityTickCount() == entity.tickCount) {
                tickCache.put(entityId, entry.snapshot());
                results[i] = entry.snapshot();
                continue;
            }

            // 缓存未命中
            uncached.add(entity);
        }

        // === 第二遍：批量计算未缓存的实体 ===
        if (!uncached.isEmpty()) {
            Map<Integer, AttributeSnapshot> computed = computeBatchSnapshots(uncached);
            for (LivingEntity entity : uncached) {
                int entityId = entity.getId();
                AttributeSnapshot snapshot = computed.get(entityId);

                // 查找原始索引
                for (int i = 0; i < entities.size(); i++) {
                    if (entities.get(i).getId() == entityId) {
                        results[i] = snapshot;
                        break;
                    }
                }

                // 存入两级缓存
                tickCache.put(entityId, snapshot);
                crossTickCache.put(entityId, new CacheEntry(snapshot, entity.tickCount));
            }
        }

        return Arrays.asList(results);
    }

    /**
     * 标记实体属性脏（需要重新计算）
     * <p>
     * 调用时机：换装、受到 Debuff、进出光环区域、装备变更等。
     * 脏标记在下次 {@link #getSnapshot} 时生效（清除跨 Tick 缓存并重新计算）。
     *
     * @param entity 目标实体
     */
    public static void markDirty(LivingEntity entity) {
        dirtyEntities.add(entity.getId());
    }

    /**
     * 清除指定实体的所有缓存
     * <p>
     * 强制清除 Tick 缓存和跨 Tick 缓存，下次访问时重新计算。
     * 比 {@link #markDirty} 更彻底（同时清除 Tick 缓存）。
     *
     * @param entity 目标实体
     */
    public static void invalidate(LivingEntity entity) {
        int entityId = entity.getId();
        tickCache.remove(entityId);
        crossTickCache.remove(entityId);
        dirtyEntities.remove(entityId);
    }

    /**
     * 服务端 Tick 末尾清理
     * <p>
     * 由 {@link SnapshotTickHandler} 在 {@code ServerTickEvent.Post} 中调用。
     * <ul>
     *   <li>清空脏标记集合（脏实体已在本次 Tick 的 getSnapshot 中处理）</li>
     *   <li>跨 Tick 缓存容量超限时清空，防止内存泄漏</li>
     * </ul>
     *
     * @param level 主世界维度（用于获取服务端实例）
     */
    public static void tickCleanup(ServerLevel level) {
        // 清空脏标记集合
        dirtyEntities.clear();

        // 跨 Tick 缓存容量保护
        if (crossTickCache.size() > MAX_CROSS_TICK_CACHE_SIZE) {
            crossTickCache.clear();
        }
    }

    // ====================================================================
    // 内部计算方法
    // ====================================================================

    /**
     * 实时计算单个实体的属性快照（无缓存）
     * <p>
     * 委托到 {@link IAttributeRegistry#createSnapshot}，
     * 内部已处理玩家/非玩家分支。
     */
    private static AttributeSnapshot computeSnapshot(LivingEntity entity) {
        return AttributeManager.getRegistry().createSnapshot(entity);
    }

    /**
     * 批量计算多个实体的属性快照（利用批量事件优化）
     * <p>
     * 策略：
     * <ul>
     *   <li>玩家：逐个调用 {@link #computeSnapshot}（玩家使用 EntityAttribute 内部缓存，无需优化）</li>
     *   <li>单个非玩家：调用 {@link #computeSnapshot}（触发单个 GatherAttributeEvent）</li>
     *   <li>多个非玩家：触发一次 {@link GatherAttributeBatchEvent}，然后逐个构建快照</li>
     * </ul>
     *
     * @param entities 未命中缓存的实体列表
     * @return entityId → AttributeSnapshot 的映射
     */
    private static Map<Integer, AttributeSnapshot> computeBatchSnapshots(List<LivingEntity> entities) {
        Map<Integer, AttributeSnapshot> result = new LinkedHashMap<>();

        // 分离玩家和非玩家实体
        List<LivingEntity> mobs = new ArrayList<>();

        for (LivingEntity entity : entities) {
            if (entity instanceof Player) {
                // 玩家：逐个计算（使用 EntityAttribute 内部管线缓存）
                result.put(entity.getId(), computeSnapshot(entity));
            } else {
                mobs.add(entity);
            }
        }

        // 非玩家实体批量处理
        if (!mobs.isEmpty()) {
            if (mobs.size() == 1) {
                // 单个非玩家实体：使用标准路径（触发 GatherAttributeEvent）
                LivingEntity mob = mobs.get(0);
                result.put(mob.getId(), computeSnapshot(mob));
            } else {
                // 多个非玩家实体：触发 GatherAttributeBatchEvent（一次事件，N 个实体）
                GatherAttributeBatchEvent batchEvent = new GatherAttributeBatchEvent(mobs);
                RPGEventBus.post(batchEvent);

                IAttributeRegistry registry = AttributeManager.getRegistry();
                for (LivingEntity mob : mobs) {
                    EntityAttributeAttachment attachment = mob.getData(
                            AttributeManager.ENTITY_ATTRIBUTE_ATTACHMENT);
                    if (!attachment.isEmpty()) {
                        // 使用批量事件的修饰符构建快照
                        result.put(mob.getId(),
                                MobSnapshotBuilder.buildWithModifiers(
                                        mob, registry, attachment,
                                        batchEvent.getEntityModifiers(mob)));
                    } else {
                        // 附件为空：降级到标准路径（读取 EntityAttribute）
                        result.put(mob.getId(), computeSnapshot(mob));
                    }
                }
            }
        }

        return result;
    }
}
