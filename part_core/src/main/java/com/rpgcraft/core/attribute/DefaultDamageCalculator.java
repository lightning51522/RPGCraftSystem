package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IDamageCalculator;
import net.minecraft.world.entity.LivingEntity;

import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link IDamageCalculator} 的默认实现
 * <p>
 * 基于 RPG 属性的伤害公式，所有数值均为整数运算：
 *
 * <h3>伤害减免公式（Incoming）</h3>
 * <ul>
 *   <li><b>物理减免：</b>{@code max(0, 原始伤害 - 防御力)}
 *     <br>示例：攻击力 30，防御力 10 → 最终伤害 = max(0, 30-10) = 20</li>
 *   <li><b>法术减免：</b>{@code (int)(原始伤害 × (1 - 法抗/100.0))}
 *     <br>示例：法术伤害 40，法抗 25 → 最终伤害 = (int)(40 × 0.75) = 30</li>
 * </ul>
 *
 * <h3>伤害输出公式（Outgoing）</h3>
 * <ul>
 *   <li><b>物理输出：</b>基础伤害 = 力量值</li>
 *   <li><b>法术输出：</b>基础伤害 = 魔力值</li>
 *   <li><b>暴击判定：</b>使用 {@link ThreadLocalRandom} 生成 [0, 100) 的随机数，
 *       若小于暴击率则触发暴击，伤害乘以 {@code (1 + 暴击伤害/100.0)}
 *     <br>示例：力量 50，暴击率 20%，暴击伤害 150% → 普攻 50，暴击 (int)(50 × 2.5) = 125</li>
 * </ul>
 */
public class DefaultDamageCalculator implements IDamageCalculator {

    @Override
    public int calculateIncomingDamage(LivingEntity entity, int originalDamage, AttackType type) {
        return switch (type) {
            case PHYSICAL -> {
                // 物理减免：直接减去防御力，最低为 0
                int defense = entity.getData(AttributeManager.DEFENSE).getValue();
                yield Math.max(0, originalDamage - defense);
            }
            case MAGIC -> {
                // 法术减免：按法抗百分比减免（法抗 100 = 完全免疫）
                int resistance = entity.getData(AttributeManager.RESISTANCE).getValue();
                yield (int) Math.max(0, originalDamage * (1.0 - resistance / 100.0));
            }
            default -> originalDamage;
        };
    }

    @Override
    public int calculateOutgoingDamage(LivingEntity entity, AttackType type) {
        // 根据攻击类型确定基础伤害
        int baseDamage = switch (type) {
            case PHYSICAL -> entity.getData(AttributeManager.STRENGTH).getValue();
            case MAGIC -> entity.getData(AttributeManager.MANA).getValue();
            default -> 0;
        };

        // 暴击判定：ThreadLocalRandom 避免多线程竞争，适合服务端并发场景
        int critRate = entity.getData(AttributeManager.CRITICAL_RATE).getValue();
        boolean isCrit = ThreadLocalRandom.current().nextInt(100) < critRate;

        if (isCrit) {
            // 暴击加成：暴击伤害 50 表示 50% 额外伤害（即 1.5 倍）
            int critRatio = entity.getData(AttributeManager.CRITICAL_RATIO).getValue();
            return (int) (baseDamage * (1.0 + critRatio / 100.0));
        }

        return baseDamage;
    }
}
