package com.rpgcraft.attributes.combat;

import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IDamageCalculator;
import com.rpgcraft.attributes.module.DefaultAttributes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * {@link IDamageCalculator} 的默认实现
 * <p>
 * 基于 RPG 属性的伤害公式，所有数值均为整数运算：
 * <p>
 * <b>属性读取策略</b>：通过 {@link AttributeSnapshotManager} 读取属性值。
 * <ul>
 *   <li>非玩家实体：快照管理器触发 {@code GatherAttributeEvent} 收集动态修饰符，
 *       通过管线计算最终值（含装备/光环/职业加成）</li>
 *   <li>玩家：快照管理器透传 {@code EntityAttribute}（自带管线缓存），无额外开销</li>
 * </ul>
 * <p>
 * 本类所引用的游戏属性 ID（DEFENSE/RESISTANCE/STRENGTH/MANA/CRITICAL_RATE/CRITICAL_RATIO/FIXED_DAMAGE/PHYSICAL_PENETRATE/MAGICAL_PENETRATE）
 * 为 {@link DefaultAttributes} 常量，与属性注册同属一个模块；
 * 对应属性未注册时读取值为 0，公式自动降级（不减免/不暴击/不穿透）。
 *
 * <h3>伤害减免公式（Incoming）</h3>
 * <ul>
 *   <li><b>物理减免：</b>{@code max(0, 原始伤害 - max(0, 防御力 - 物理穿透))}
 *     <br>示例：攻击力 30，防御力 10，物理穿透 3 → 有效防御 = 7，最终伤害 = 23</li>
 *   <li><b>法术减免：</b>{@code (int)(原始伤害 × (1 - max(0, 法抗 - 法术穿透)/100.0))}
 *     <br>示例：法术伤害 40，法抗 25，法术穿透 10 → 有效法抗 = 15，最终伤害 = (int)(40 × 0.85) = 34</li>
 * </ul>
 *
 * <h3>伤害输出公式（Outgoing）</h3>
 * <ul>
 *   <li><b>物理输出：</b>基础伤害 = 力量值</li>
 *   <li><b>法术输出：</b>基础伤害 = 魔力值</li>
 *   <li><b>多层暴击判定：</b>暴击率上限 300%，每 100% 保底增加一层暴击伤害，
 *       超出部分作为下一层的触发概率。
 *     <br>暴击率 120% → 保底 1 层 + 20% 概率第 2 层
 *     <br>暴击率 300% → 保底 3 层暴击</li>
 * </ul>
 */
public class DefaultDamageCalculator implements IDamageCalculator {

    @Override
    public int calculateIncomingDamage(LivingEntity entity, int originalDamage, AttackType type) {
        // 无攻击方信息时退化为原公式（穿透为 0）
        return calculateIncomingDamage(entity, originalDamage, type, null);
    }

    @Override
    public int calculateIncomingDamage(LivingEntity target, int originalDamage,
                                        AttackType type, @Nullable LivingEntity attacker) {
        // 读取攻击方穿透属性（无攻击方时为 0）
        int physicalPenetrate = attacker != null
                ? getAttributeValue(attacker, DefaultAttributes.PHYSICAL_PENETRATE_ID) : 0;
        int magicalPenetrate = attacker != null
                ? getAttributeValue(attacker, DefaultAttributes.MAGICAL_PENETRATE_ID) : 0;

        return switch (type) {
            case PHYSICAL -> {
                // 物理减免：防御力被物理穿透降低后，再从原始伤害中扣除
                int defense = getAttributeValue(target, DefaultAttributes.DEFENSE_ID);
                int effectiveDefense = Math.max(0, defense - physicalPenetrate);
                yield Math.max(0, originalDamage - effectiveDefense);
            }
            case MAGIC -> {
                // 法术减免：法抗被法术穿透降低后，按百分比减免
                int resistance = getAttributeValue(target, DefaultAttributes.RESISTANCE_ID);
                int effectiveResistance = Math.max(0, resistance - magicalPenetrate);
                yield (int) Math.max(0, originalDamage * (1.0 - effectiveResistance / 100.0));
            }
            case MIX_TYPE -> {
                // 混合减免：物理部分受物理穿透，魔法部分受法术穿透
                int half = originalDamage / 2;
                int defense = getAttributeValue(target, DefaultAttributes.DEFENSE_ID);
                int resistance = getAttributeValue(target, DefaultAttributes.RESISTANCE_ID);
                int effectiveDefense = Math.max(0, defense - physicalPenetrate);
                int effectiveResistance = Math.max(0, resistance - magicalPenetrate);
                int physicalPart = Math.max(0, half - effectiveDefense);
                int magicPart = (int) Math.max(0, half * (1.0 - effectiveResistance / 100.0));
                yield physicalPart + magicPart;
            }
            default -> originalDamage;
        };
    }

    @Override
    public int calculateOutgoingDamage(LivingEntity entity, AttackType type) {
        // 混合伤害：分别取力量和魔力的一半，统一暴击后相加
        if (type == AttackType.MIX_TYPE) {
            int strHalf = getAttributeValue(entity, DefaultAttributes.STRENGTH_ID) / 2;
            int manaHalf = getAttributeValue(entity, DefaultAttributes.MANA_ID) / 2;
            double multiplier = rollCriticalMultiplier(entity);
            int fixedDmg = getAttributeValue(entity, DefaultAttributes.FIXED_DAMAGE_ID);
            return (int) (strHalf * multiplier) + (int) (manaHalf * multiplier) + fixedDmg;
        }

        // 根据攻击类型确定基础伤害
        int baseDamage = switch (type) {
            case PHYSICAL -> getAttributeValue(entity, DefaultAttributes.STRENGTH_ID);
            case MAGIC -> getAttributeValue(entity, DefaultAttributes.MANA_ID);
            default -> 0;
        };

        // 多层暴击判定
        double multiplier = rollCriticalMultiplier(entity);
        int fixedDmg = getAttributeValue(entity, DefaultAttributes.FIXED_DAMAGE_ID);
        return (int) (baseDamage * multiplier) + fixedDmg;
    }

    /**
     * 多层暴击判定
     * <p>
     * 暴击率每 100% 保底增加一层暴击伤害，超出部分作为下一层的触发概率。
     * <ul>
     *   <li>暴击率 50%  → 50% 概率 1 层暴击（正常暴击）</li>
     *   <li>暴击率 120% → 保底 1 层 + 20% 概率第 2 层</li>
     *   <li>暴击率 220% → 保底 2 层 + 20% 概率第 3 层</li>
     *   <li>暴击率 300% → 保底 3 层暴击</li>
     * </ul>
     * 每层暴击将伤害乘以 {@code (1 + 暴击伤害/100.0)}，多层为幂次叠加。
     *
     * @param entity 攻击实体
     * @return 暴击倍率（≥1.0，1.0 表示未暴击）
     */
    private double rollCriticalMultiplier(LivingEntity entity) {
        int critRate = getAttributeValue(entity, DefaultAttributes.CRITICAL_RATE_ID);
        if (critRate <= 0) return 1.0;

        // 保底暴击层数 + 额外一层概率
        int fullCrits = critRate / 100;
        int remainder = critRate % 100;
        int totalCrits = fullCrits;

        if (remainder > 0 && ThreadLocalRandom.current().nextInt(100) < remainder) {
            totalCrits++;
        }

        if (totalCrits == 0) return 1.0;

        // 每层暴击乘以 (1 + critRatio/100)，多层为幂次
        int critRatio = getAttributeValue(entity, DefaultAttributes.CRITICAL_RATIO_ID);
        double critMulti = 1.0 + critRatio / 100.0;
        return Math.pow(critMulti, totalCrits);
    }

    /**
     * 通过快照管理器读取实体属性值
     * <p>
     * 对非玩家实体：触发 {@code GatherAttributeEvent} 收集动态修饰符，
     * 通过 {@code AttributePipeline} 计算最终值（含装备/光环/职业加成），
     * 结果缓存在 Tick 级和跨 Tick 缓存中。
     * <p>
     * 对玩家：透传 {@code EntityAttribute}（自带管线缓存），
     * 快照管理器仅提供统一入口，无额外开销。
     *
     * @param entity 目标实体
     * @param attrId 属性标识符
     * @return 属性当前值（未找到返回 0）
     */
    private static int getAttributeValue(LivingEntity entity, Identifier attrId) {
        AttributeSnapshot snapshot = AttributeSnapshotManager.getSnapshot(entity);
        AttributeSnapshot.AttributeData data = snapshot.get(attrId);
        return data != null ? data.currentValue() : 0;
    }
}
