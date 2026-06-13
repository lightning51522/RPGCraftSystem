package com.rpgcraft.core.attribute.api;

import com.rpgcraft.core.attribute.AttackType;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * 属性伤害计算接口
 * <p>
 * 定义基于 RPG 属性的伤害减免和输出计算公式。默认实现由 combat 模块提供
 *（{@code com.rpgcraft.combat.DefaultDamageCalculator}）。
 * <p>
 * 其他模组可以替换此实现来提供更复杂的伤害公式（元素克制、装备加成等），
 * 通过 {@link com.rpgcraft.core.attribute.AttributeManager#setDamageCalculator(IDamageCalculator)} 注入。
 */
public interface IDamageCalculator {

    /**
     * 计算实体受到伤害后的最终数值（减免后）
     *
     * @param target         受击实体
     * @param originalDamage 原始伤害（减免前）
     * @param type           伤害类型
     * @return 减免后的最终伤害（不低于 0）
     */
    int calculateIncomingDamage(LivingEntity target, int originalDamage, AttackType type);

    /**
     * 计算承伤（含攻击方穿透属性）
     * <p>
     * 默认实现忽略攻击方，委托给 {@link #calculateIncomingDamage(LivingEntity, int, AttackType)}。
     * combat 模块的默认实现会读取攻击方的物理/法术穿透属性来降低目标的有效防御/法抗。
     *
     * @param target         受击实体
     * @param originalDamage 原始伤害（减免前）
     * @param type           伤害类型
     * @param attacker       攻击方实体（null 时无穿透效果）
     * @return 减免后的最终伤害（不低于 0）
     */
    default int calculateIncomingDamage(LivingEntity target, int originalDamage,
                                         AttackType type, @Nullable LivingEntity attacker) {
        return calculateIncomingDamage(target, originalDamage, type);
    }

    /**
     * 计算实体造成的伤害数值（含暴击）
     *
     * @param attacker 攻击方实体
     * @param type     攻击类型
     * @return 计算后的伤害数值
     */
    int calculateOutgoingDamage(LivingEntity attacker, AttackType type);
}
