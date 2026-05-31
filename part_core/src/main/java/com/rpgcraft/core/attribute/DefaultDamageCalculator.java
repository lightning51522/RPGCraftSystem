package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IDamageCalculator;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link IDamageCalculator} 的默认实现
 * <p>
 * 基于 RPG 属性的伤害公式：
 * <ul>
 *   <li>物理伤害减免：max(0, 原始伤害 - 防御力)</li>
 *   <li>法术伤害减免：原始伤害 × (1 - 法抗%)</li>
 *   <li>物理输出：力量为基础，暴击加成</li>
 *   <li>法术输出：魔力为基础，暴击加成</li>
 * </ul>
 */
public class DefaultDamageCalculator implements IDamageCalculator {

    @Override
    public int calculateIncomingDamage(LivingEntity entity, int originalDamage, AttackType type) {
        return switch (type) {
            case PHYSICAL -> {
                int defense = entity.getData(GenericEntityData.DEFENSE).getValue();
                yield Math.max(0, originalDamage - defense);
            }
            case MAGIC -> {
                int resistance = entity.getData(GenericEntityData.RESISTANCE).getValue();
                yield (int) Math.max(0, originalDamage * (1.0 - resistance / 100.0));
            }
            default -> originalDamage;
        };
    }

    @Override
    public int calculateOutgoingDamage(LivingEntity entity, AttackType type) {
        int baseDamage = switch (type) {
            case PHYSICAL -> entity.getData(GenericEntityData.STRENGTH).getValue();
            case MAGIC -> entity.getData(GenericEntityData.MANA).getValue();
            default -> 0;
        };

        int critRate = entity.getData(GenericEntityData.CRITICAL_RATE).getValue();
        boolean isCrit = ThreadLocalRandom.current().nextInt(100) < critRate;

        if (isCrit) {
            int critRatio = entity.getData(GenericEntityData.CRITICAL_RATIO).getValue();
            return (int) (baseDamage * (1.0 + critRatio / 100.0));
        }

        return baseDamage;
    }
}
