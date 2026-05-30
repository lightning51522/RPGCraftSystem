package com.rpgcraft.core.attribute.api;

import com.rpgcraft.core.attribute.AttackType;
import net.minecraft.world.entity.LivingEntity;

/**
 * 战斗伤害计算接口
 * <p>
 * 定义伤害减免和输出的计算公式。默认实现为
 * {@link com.rpgcraft.core.attribute.DefaultCombatCalculator}。
 * <p>
 * 其他模组可以替换此实现来提供更复杂的战斗公式（元素克制、装备加成等）。
 */
public interface ICombatCalculator {

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
     * 计算实体造成的伤害数值（含暴击）
     *
     * @param attacker 攻击方实体
     * @param type     攻击类型
     * @return 计算后的伤害数值
     */
    int calculateOutgoingDamage(LivingEntity attacker, AttackType type);
}
