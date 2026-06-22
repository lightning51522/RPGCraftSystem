package com.rpgcraft.core.event.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.event.RPGEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/**
 * 单实体属性收集事件
 * <p>
 * 在构建非玩家实体属性快照时触发。模块监听此事件，
 * 根据实体状态（手持物、位置、光环等）向 {@link #modifiers} Map 中注入修饰符。
 * <p>
 * <h3>使用场景</h3>
 * <ul>
 *   <li>装备模块：检查实体手持物，注入攻击力/防御修饰符</li>
 *   <li>光环模块：检查实体位置是否在光环范围内，注入增益/减益</li>
 *   <li>职业模块：检查实体职业，注入职业专属加成</li>
 * </ul>
 * <p>
 * <h3>注册示例</h3>
 * <pre>{@code
 * RPGEventBus.register(GatherAttributeEvent.class, event -> {
 *     LivingEntity entity = event.getEntity();
 *     if (isInAuraRange(entity)) {
 *         event.addModifier(STRENGTH_ID,
 *             new SimpleAttributeModifier(AURA_SOURCE_ID, Operation.ADDITION, 10));
 *     }
 * }, RPGEvent.PRIORITY_NORMAL);
 * }</pre>
 *
 * @see GatherAttributeBatchEvent
 * @see com.rpgcraft.core.attribute.api.IAttributeModifier
 */
public class GatherAttributeEvent extends RPGEvent {

    /** 目标实体 */
    private final LivingEntity entity;

    /**
     * 收集到的修饰符：attrId → 修饰符列表
     * <p>
     * 模块通过 {@link #addModifier} 向此 Map 注入修饰符，
     * 快照构建器在事件完成后读取所有收集到的修饰符。
     */
    private final Map<Identifier, List<IAttributeModifier>> modifiers;

    /**
     * 构造属性收集事件
     *
     * @param entity    目标实体
     * @param modifiers 可变的修饰符容器（由快照构建器提供）
     */
    public GatherAttributeEvent(LivingEntity entity,
                                Map<Identifier, List<IAttributeModifier>> modifiers) {
        this.entity = entity;
        this.modifiers = modifiers;
    }

    /** 获取目标实体 */
    public LivingEntity getEntity() {
        return entity;
    }

    /**
     * 添加属性修饰符
     * <p>
     * 模块在事件回调中调用此方法注入修饰符。
     * 相同 attrId 的修饰符会累加到同一列表中。
     *
     * @param attrId   属性标识符（如 {@code Identifier.fromNamespaceAndPath("rpgcraftcore", "strength")}）
     * @param modifier 属性修饰符
     */
    public void addModifier(Identifier attrId, IAttributeModifier modifier) {
        modifiers.computeIfAbsent(attrId, k -> new ArrayList<>()).add(modifier);
    }

    /**
     * 获取指定属性的已收集修饰符（只读视图）
     *
     * @param attrId 属性标识符
     * @return 修饰符列表（可能为空，不为 null）
     */
    public List<IAttributeModifier> getModifiers(Identifier attrId) {
        List<IAttributeModifier> list = modifiers.get(attrId);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * 获取所有已收集的修饰符（只读快照）
     * <p>
     * 返回的 Map 是当前修饰符的不可变快照，后续 {@link #addModifier} 不会影响已返回的快照。
     *
     * @return attrId → 修饰符列表 的不可变 Map
     */
    public Map<Identifier, List<IAttributeModifier>> getAllModifiers() {
        Map<Identifier, List<IAttributeModifier>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<Identifier, List<IAttributeModifier>> entry : modifiers.entrySet()) {
            snapshot.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(snapshot);
    }
}
