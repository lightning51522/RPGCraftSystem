package com.rpgcraft.attributepoints;

import com.rpgcraft.attributepoints.network.SyncPlayerAttributePointsPacket;
import com.rpgcraft.core.attribute.AttributeIds;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeModifier;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.attributepoints.PlayerAttributePoints;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.network.SyncAttributeSnapshotPacket;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.registry.IAttributePointSystem;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 属性点模块全局门面
 * <p>
 * 保留对 {@link PlayerAttributePoints} 附件的静态引用，实现 {@link IAttributePointSystem}。
 * 属性点作为 {@code ADDITION} 修饰符应用到对应属性上（与职业加成对称的设计）。
 * <p>
 * <h3>可分配属性</h3>
 * 动态从 {@link AttributeManager#getRegistry()} 遍历，过滤 {@code shouldResetOnRespawn() == false}。
 * 这自动排除 life/skill_point（资源型），并自动接纳第三方新增的非资源型属性，
 * 满足"适应属性增减"的需求。
 * <p>
 * <h3>修饰符生命周期</h3>
 * 修饰符不参与序列化（{@code EntityAttribute.CODEC} 只存基础值），因此：
 * <ul>
 *   <li>分配/重置时：{@code removeModifier} + {@code addModifier} 重算</li>
 *   <li>登录时：由 {@code AttributePointsLoginEventHandler} 调用 {@link #reapplyAllModifiers}</li>
 *   <li>重生时：由 {@code AttributePointsSnapshotContributor} 调用 {@link #reapplyAllModifiers}</li>
 * </ul>
 *
 * @apiNote 内部 API — 第三方模组应通过 {@link RPGSystems} 门面访问属性点系统功能，
 *          不应直接依赖此类。
 */
public class AttributePointsManager {

    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    /** 修饰符来源命名空间 */
    private static final String MODIFIER_PREFIX = "rpgcraftattributepoints";

    // ====================================================================
    // 属性点系数（每点分配 = 多少属性值）
    // ====================================================================
    // 大多数属性 1 点 = 1 属性值（系数 1.0）。少数属性采用小数系数，
    // 用「分子 / 分母」的整数比表达，避免浮点累积误差：
    //   实际加成 = allocated × numerator / denominator（整数除法，向下取整）
    // 例：系数 0.1 → numerator=1, denominator=10 → 每 10 点 = 1 属性值
    //    系数 0.2 → numerator=2, denominator=10 → 每 5 点 = 1 属性值

    /** 单个属性点的系数（numerator/denominator，默认 1/1） */
    private record Coefficient(int numerator, int denominator) {}

    /** 默认系数：1 点 = 1 属性值 */
    private static final Coefficient DEFAULT_COEFFICIENT = new Coefficient(1, 1);

    /**
     * 各属性点系数表（按属性 ID 查询，属性 ID 真相源为 {@link AttributeIds}）。
     * <p>
     * 当前特例：
     * <ul>
     *   <li>{@code resistance}（法抗）：0.1 —— 每 10 点 = 1 法抗</li>
     *   <li>{@code magical_penetrate}（法术穿透）：0.1 —— 每 10 点 = 1 法术穿透</li>
     *   <li>{@code physical_penetrate}（物理穿透）：0.2 —— 每 5 点 = 1 物理穿透</li>
     * </ul>
     * 未列出的属性使用默认系数 1.0。
     */
    private static final Map<Identifier, Coefficient> COEFFICIENTS = Map.of(
            AttributeIds.RESISTANCE_ID, new Coefficient(1, 10),
            AttributeIds.MAGICAL_PENETRATE_ID, new Coefficient(1, 10),
            AttributeIds.PHYSICAL_PENETRATE_ID, new Coefficient(2, 10)
    );

    /**
     * 计算指定属性分配 {@code allocated} 点后的实际属性值加成（整数）。
     * <p>
     * 按属性系数 {@code allocated × numerator / denominator} 计算（整数除法向下取整）。
     * 例：法抗分配 25 点、系数 0.1 → 加成 2（25/10）。
     *
     * @param attrId   属性 ID
     * @param allocated 已分配点数（≥ 0）
     * @return 实际属性值加成
     */
    private static int bonusFor(Identifier attrId, int allocated) {
        Coefficient c = COEFFICIENTS.getOrDefault(attrId, DEFAULT_COEFFICIENT);
        return allocated * c.numerator() / c.denominator();
    }

    /** PlayerAttributePoints 附件 Supplier */
    public static Supplier<AttachmentType<PlayerAttributePoints>> PLAYER_ATTRIBUTE_POINTS;

    /**
     * 初始化属性点模块
     * <p>
     * 注册附件类型、注册到 {@link RPGSystems} 统一门面。
     */
    public static void init() {
        deferredRegister = DeferredRegister.create(
                NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_ATTRIBUTE_POINTS = deferredRegister.register("player_attribute_points",
                () -> AttachmentType.builder(PlayerAttributePoints::new)
                        .serialize(PlayerAttributePoints.CODEC)
                        .build()
        );

        // 注册附件 Supplier 到 RPGSystems（供客户端 UI 访问）
        RPGSystems.registerPlayerAttributePointsAttachment(PLAYER_ATTRIBUTE_POINTS);

        // 注册到 RPGSystems 统一门面
        RPGSystems.registerAttributePointSystem(new IAttributePointSystem() {
            @Override
            public int getAvailablePoints(ServerPlayer player) {
                return player.getData(PLAYER_ATTRIBUTE_POINTS).getAvailablePoints();
            }

            @Override
            public int getAllocatedPoints(ServerPlayer player, Identifier attrId) {
                return player.getData(PLAYER_ATTRIBUTE_POINTS).getAllocated(attrId);
            }

            @Override
            public Map<Identifier, Integer> getAllAllocations(ServerPlayer player) {
                return player.getData(PLAYER_ATTRIBUTE_POINTS).getAllocations();
            }

            @Override
            public boolean allocate(ServerPlayer player, Identifier attrId, int points) {
                return AttributePointsManager.allocate(player, attrId, points);
            }

            @Override
            public boolean deallocate(ServerPlayer player, Identifier attrId, int points) {
                return AttributePointsManager.deallocate(player, attrId, points);
            }

            @Override
            public void grantPoints(ServerPlayer player, int points) {
                AttributePointsManager.grantPoints(player, points);
            }

            @Override
            public void reset(ServerPlayer player) {
                AttributePointsManager.reset(player);
            }

            @Override
            public void syncToClient(ServerPlayer player) {
                AttributePointsManager.syncToClient(player);
            }
        });
    }

    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    // ====================================================================
    // 可分配属性发现
    // ====================================================================

    /**
     * 获取所有可分配属性（动态计算，适应属性注册变化）
     * <p>
     * 过滤条件：{@link IAttributeEntry#isAllocatable()} —— 排除 life/skill_point（资源型）
     * 和暴击率/暴击伤害（综合派生属性，不可加点）。
     *
     * @return 可分配属性条目列表
     */
    public static List<IAttributeEntry> getAllocatableEntries() {
        return AttributeManager.getRegistry().getAllEntries().stream()
                .filter(IAttributeEntry::isAllocatable)
                .toList();
    }

    /**
     * 判断属性是否可分配
     */
    public static boolean isAllocatable(Identifier attrId) {
        IAttributeEntry entry = AttributeManager.getRegistry().getEntry(attrId);
        return entry != null && entry.isAllocatable();
    }

    // ====================================================================
    // 点数操作
    // ====================================================================

    /**
     * 授予玩家可分配点数
     *
     * @param player 服务端玩家
     * @param count  授予点数（必须 &gt; 0）
     */
    public static void grantPoints(ServerPlayer player, int count) {
        if (count <= 0) return;
        PlayerAttributePoints data = player.getData(PLAYER_ATTRIBUTE_POINTS);
        data.addAvailablePoints(count);
        syncToClient(player);
    }

    /**
     * 分配属性点到指定属性
     * <p>
     * 校验通过后：扣减可分配点数、累加 allocations、重新应用该属性的修饰符、同步。
     *
     * @param player 服务端玩家
     * @param attrId 目标属性 ID
     * @param points 分配点数（必须 &gt; 0）
     * @return {@code true} 成功；{@code false} 校验失败
     */
    public static boolean allocate(ServerPlayer player, Identifier attrId, int points) {
        if (points <= 0) return false;
        if (!isAllocatable(attrId)) return false;

        PlayerAttributePoints data = player.getData(PLAYER_ATTRIBUTE_POINTS);
        if (data.getAvailablePoints() < points) return false;

        // 扣减可分配点数 + 累加分配
        data.addAvailablePoints(-points);
        data.addAllocation(attrId, points);

        // 重新应用该属性的修饰符
        reapplyModifier(player, attrId);

        // 同步点数数据 + 该属性 + 全量快照到客户端
        // 全量快照刷新保证角色界面（读 UISnapshotCache 的左侧属性列表）实时更新
        syncAll(player);

        return true;
    }

    /**
     * 从指定属性回收已分配的点数
     * <p>
     * 校验通过后：扣减 allocations、增加可分配点数、重新应用该属性的修饰符、同步。
     *
     * @param player 服务端玩家
     * @param attrId 目标属性 ID
     * @param points 回收点数（必须 &gt; 0）
     * @return {@code true} 成功；{@code false} 校验失败
     */
    public static boolean deallocate(ServerPlayer player, Identifier attrId, int points) {
        // 配置禁用减少时拒绝所有回收请求（防作弊：即便客户端绕过 UI 直接发包也会被拒）
        if (!AttributePointsConfig.isAllowDecrease()) return false;
        if (points <= 0) return false;
        if (!isAllocatable(attrId)) return false;

        PlayerAttributePoints data = player.getData(PLAYER_ATTRIBUTE_POINTS);
        int allocated = data.getAllocated(attrId);
        if (allocated < points) return false;

        // 扣减分配 + 增加可分配点数
        data.addAllocation(attrId, -points);
        data.addAvailablePoints(points);

        // 重新应用该属性的修饰符（分配归零时移除修饰符）
        reapplyModifier(player, attrId);

        // 同步
        syncAll(player);

        return true;
    }

    /**
     * 重置玩家全部分配（退还所有已分配点数为可分配，移除全部修饰符）
     *
     * @param player 服务端玩家
     */
    public static void reset(ServerPlayer player) {
        PlayerAttributePoints data = player.getData(PLAYER_ATTRIBUTE_POINTS);

        // 移除所有已分配属性的修饰符
        for (Identifier attrId : data.getAllocations().keySet()) {
            IAttributeEntry entry = AttributeManager.getRegistry().getEntry(attrId);
            if (entry != null) {
                IAttribute attr = player.getData(entry.getSupplier());
                attr.removeModifier(modifierSourceId(attrId));
            }
        }

        // 退还可分配点数并清空分配映射
        data.resetAll();

        // 同步
        syncToClient(player);
        for (Identifier attrId : data.getAllocations().keySet()) {
            syncAttribute(player, attrId);
        }
    }

    // ====================================================================
    // 修饰符应用
    // ====================================================================

    /**
     * 生成属性点修饰符的来源标识符
     *
     * @param attrId 属性标识符
     * @return 修饰符来源标识符
     */
    private static Identifier modifierSourceId(Identifier attrId) {
        return Identifier.fromNamespaceAndPath(MODIFIER_PREFIX,
                "point_" + attrId.getNamespace() + "_" + attrId.getPath());
    }

    /**
     * 重新应用单个属性的修饰符（先移除再添加，避免同 sourceId 累加）
     * <p>
     * 实际加成按属性系数换算（{@link #bonusFor}）：法抗/法术穿透每 10 点 = 1 属性值，
     * 物理穿透每 5 点 = 1 属性值，其余 1 点 = 1 属性值。换算后加成为 0 时不添加修饰符
     * （例：法抗分配 5 点、系数 0.1 → 加成 0）。
     *
     * @param player 服务端玩家
     * @param attrId 属性 ID
     */
    private static void reapplyModifier(ServerPlayer player, Identifier attrId) {
        IAttributeEntry entry = AttributeManager.getRegistry().getEntry(attrId);
        if (entry == null) return;

        IAttribute attr = player.getData(entry.getSupplier());
        Identifier sourceId = modifierSourceId(attrId);
        attr.removeModifier(sourceId);

        int allocated = player.getData(PLAYER_ATTRIBUTE_POINTS).getAllocated(attrId);
        if (allocated > 0) {
            // 按属性系数换算为实际属性值加成（法抗/法术穿透 0.1，物理穿透 0.2，其余 1.0）
            int bonus = bonusFor(attrId, allocated);
            if (bonus > 0) {
                attr.addModifier(AttributeModifier.of(sourceId, Operation.ADDITION, bonus));
            }
        }
    }

    /**
     * 重新应用玩家全部分配的修饰符
     * <p>
     * 登录和重生时调用 —— 修饰符不序列化，必须从 allocations 重建。
     * <p>
     * <b>执行时机依赖</b>：必须在 {@code AttributeSnapshotContributor} 恢复基础值之后调用，
     * 否则修饰符会应用在未恢复的基础上。
     *
     * @param player 服务端玩家
     */
    public static void reapplyAllModifiers(ServerPlayer player) {
        PlayerAttributePoints data = player.getData(PLAYER_ATTRIBUTE_POINTS);
        for (Identifier attrId : data.getAllocations().keySet()) {
            reapplyModifier(player, attrId);
        }
    }

    // ====================================================================
    // 网络同步
    // ====================================================================

    /**
     * 同步玩家属性点数据到客户端
     *
     * @param player 服务端玩家
     */
    public static void syncToClient(ServerPlayer player) {
        PlayerAttributePoints data = player.getData(PLAYER_ATTRIBUTE_POINTS);
        SyncPlayerAttributePointsPacket.sendToClient(player, data);
    }

    /**
     * 分配/回收后的全量同步：点数数据 + 全量属性快照
     * <p>
     * 全量快照刷新保证角色界面（读 {@code UISnapshotCache} 的左侧属性列表）实时更新，
     * 而不必重新打开面板。
     *
     * @param player 服务端玩家
     */
    private static void syncAll(ServerPlayer player) {
        // 点数数据同步（右侧属性点面板读客户端附件）
        syncToClient(player);
        // 全量属性快照同步（左侧属性列表读 UISnapshotCache）
        AttributeSnapshot snapshot = AttributeManager.getRegistry().createSnapshot(player);
        SyncAttributeSnapshotPacket.sendToClient(player, snapshot);
    }

    /**
     * 同步单个属性的计算值到客户端
     */
    private static void syncAttribute(ServerPlayer player, Identifier attrId) {
        IAttributeEntry entry = AttributeManager.getRegistry().getEntry(attrId);
        if (entry == null) return;
        EntityAttribute attr = (EntityAttribute) player.getData(entry.getSupplier());
        SyncPlayerAttributePacket.sendToClient(player, attrId, attr);
    }
}
