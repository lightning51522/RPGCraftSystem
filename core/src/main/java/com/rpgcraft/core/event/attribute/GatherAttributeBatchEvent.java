package com.rpgcraft.core.event.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.event.RPGEvent;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/**
 * 批量实体属性收集事件（AOE 优化）
 * <p>
 * 当需要同时为多个实体计算属性快照时（如 AOE 伤害场景），
 * 使用此事件代替逐个触发 {@link GatherAttributeEvent}，避免 N 次事件分发的开销。
 * <p>
 * 事件只触发一次，模块在回调中循环处理所有实体，一次性注入修饰符。
 * 内部使用 {@code entity.getId()}（int）作为 key，避免 UUID 哈希开销。
 * <p>
 * <h3>使用场景</h3>
 * <ul>
 *   <li>AOE 技能命中 50 个怪物 → 触发一次批量事件，模块循环 50 个实体</li>
 *   <li>光环效果影响区域内所有实体 → 批量收集增益/减益修饰符</li>
 * </ul>
 * <p>
 * <h3>注册示例</h3>
 * <pre>{@code
 * RPGEventBus.register(GatherAttributeBatchEvent.class, event -> {
 *     for (LivingEntity entity : event.getEntities()) {
 *         if (isInAuraRange(entity)) {
 *             event.addModifier(entity, DEFENSE_ID,
 *                 new SimpleAttributeModifier(AURA_SOURCE_ID, Operation.ADDITION, 10));
 *         }
 *     }
 * }, RPGEvent.PRIORITY_NORMAL);
 * }</pre>
 *
 * @see GatherAttributeEvent
 * @see com.rpgcraft.core.attribute.api.IAttributeModifier
 */
public class GatherAttributeBatchEvent extends RPGEvent {

    /** 需要收集属性的实体列表（只读） */
    private final List<LivingEntity> entities;

    /**
     * 每个实体的修饰符收集容器：entity.getId() → (attrId → 修饰符列表)
     * <p>
     * 使用 int key 的 fastutil Map，比 {@code HashMap<UUID, ...>} 快约 3-5 倍。
     */
    private final Int2ObjectMap<Map<Identifier, List<IAttributeModifier>>> entityModifiers;

    /**
     * 构造批量属性收集事件
     *
     * @param entities 需要收集属性的实体列表
     */
    public GatherAttributeBatchEvent(List<LivingEntity> entities) {
        this.entities = Collections.unmodifiableList(new ArrayList<>(entities));
        this.entityModifiers = new Int2ObjectOpenHashMap<>(entities.size());
    }

    /** 获取需要收集属性的实体列表（只读） */
    public List<LivingEntity> getEntities() {
        return entities;
    }

    /**
     * 为指定实体添加属性修饰符
     *
     * @param entity   目标实体（必须在 {@link #getEntities()} 列表中）
     * @param attrId   属性标识符
     * @param modifier 属性修饰符
     */
    public void addModifier(LivingEntity entity, Identifier attrId, IAttributeModifier modifier) {
        entityModifiers
                .computeIfAbsent(entity.getId(), k -> new LinkedHashMap<>())
                .computeIfAbsent(attrId, k -> new ArrayList<>())
                .add(modifier);
    }

    /**
     * 获取指定实体的某个属性的已收集修饰符（只读视图）
     *
     * @param entity 目标实体
     * @param attrId 属性标识符
     * @return 修饰符列表（可能为空，不为 null）
     */
    public List<IAttributeModifier> getModifiers(LivingEntity entity, Identifier attrId) {
        Map<Identifier, List<IAttributeModifier>> attrMap = entityModifiers.get(entity.getId());
        if (attrMap == null) return Collections.emptyList();
        List<IAttributeModifier> list = attrMap.get(attrId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * 获取指定实体的所有已收集修饰符（只读快照）
     *
     * @param entity 目标实体
     * @return attrId → 修饰符列表 的不可变 Map，无修饰符则返回空 Map
     */
    public Map<Identifier, List<IAttributeModifier>> getEntityModifiers(LivingEntity entity) {
        Map<Identifier, List<IAttributeModifier>> attrMap = entityModifiers.get(entity.getId());
        if (attrMap == null) return Collections.emptyMap();
        Map<Identifier, List<IAttributeModifier>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Identifier, List<IAttributeModifier>> entry : attrMap.entrySet()) {
            snapshot.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
