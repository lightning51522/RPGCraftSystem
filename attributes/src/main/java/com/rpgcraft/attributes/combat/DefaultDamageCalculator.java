package com.rpgcraft.attributes.combat;

import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IDamageCalculator;
import com.rpgcraft.core.profession.api.ProfessionFormulas;
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
 * 本类所引用的游戏属性 ID（STRENGTH/INTELLIGENCE/AGILE/PRECISION/RESISTANCE/CRITICAL_RATE/CRITICAL_RATIO/
 * FIXED_DAMAGE/PHYSICAL_PENETRATE/MAGICAL_PENETRATE）
 * 为 {@link DefaultAttributes} 常量，与属性注册同属一个模块；
 * 对应属性未注册时读取值为 0，公式自动降级（不减免/不暴击/不穿透）。
 *
 * <h3>综合属性（攻击力 / 防御力）</h3>
 * 攻击力和防御力<b>不作为真实属性存储</b>，由本类在计算伤害时根据一般属性动态派生。
 * 派生公式<b>由玩家的当前主职业提供</b>（{@link IProfession#computePhysicalAttack 等}），
 * 怪物或无职业模块时回退默认公式（见 {@link ProfessionFormulas}）：
 * <ul>
 *   <li><b>物理攻击力</b>默认 = {@code 力量×2 + 智力}</li>
 *   <li><b>魔法攻击力</b>默认 = {@code 智力×2 + 力量}</li>
 *   <li><b>物理防御力</b>默认 = {@code 力量×2}（目标的力量值；魔法防御力仅来自装备）</li>
 * </ul>
 * 装备对一般属性（力量/智力等）的加成会自动通过属性管线计入上式（读取的是管线最终值）。
 *
 * <h3>伤害减免公式（Incoming）</h3>
 * <ul>
 *   <li><b>物理减免：</b>{@code max(0, 原始伤害 - max(0, 物理防御力 - 物理穿透))}
 *     <br>其中物理防御力 = 目标力量×2（+装备对力量的加成）</li>
 *   <li><b>法术减免：</b>{@code (int)(原始伤害 × (1 - max(0, 法抗 - 法术穿透)/100.0))}
 *     <br>魔法防御力仅来自装备（无属性派生），故魔法路径不读取防御力</li>
 * </ul>
 *
 * <h3>伤害输出公式（Outgoing）</h3>
 * <ul>
 *   <li><b>物理输出：</b>基础伤害 = 力量×2 + 智力，乘暴击倍率后加固定伤害</li>
 *   <li><b>法术输出：</b>基础伤害 = 智力×2 + 力量，乘暴击倍率后加固定伤害</li>
 *   <li><b>混合输出：</b>物理部分（力量×2 + 智力）的一半 + 魔法部分（智力×2 + 力量）的一半</li>
 * </ul>
 *
 * <h3>暴击派生（敏捷 / 精准）</h3>
 * 暴击率/暴击伤害本身是可加点的一般属性，同时额外从敏捷/精准获得派生加成：
 * <ul>
 *   <li><b>有效暴击率</b> = {@code 暴击率 + 敏捷/5}（每 5 点敏捷 +1 暴击率）</li>
 *   <li><b>有效暴击伤害</b> = {@code 暴击伤害 + (精准/5)×2}（每 5 点精准 +2 暴击伤害）</li>
 * </ul>
 *
 * <h3>多层暴击判定</h3>
 * 暴击率每 100% 保底增加一层暴击伤害，超出部分作为下一层的触发概率。
 * 每层暴击将伤害乘以 {@code (1 + 有效暴击伤害/100.0)}，多层为幂次叠加。
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
                // 物理防御力 = 目标力量×2（综合属性，动态计算）
                int defense = computePhysicalDefense(target);
                int effectiveDefense = Math.max(0, defense - physicalPenetrate);
                yield Math.max(0, originalDamage - effectiveDefense);
            }
            case MAGIC -> {
                // 法术减免：法抗被法术穿透降低后，按百分比减免
                // 魔法防御力仅来自装备（无属性派生），故此处不读取防御力
                int resistance = getAttributeValue(target, DefaultAttributes.RESISTANCE_ID);
                int effectiveResistance = Math.max(0, resistance - magicalPenetrate);
                yield (int) Math.max(0, originalDamage * (1.0 - effectiveResistance / 100.0));
            }
            case MIX_TYPE -> {
                // 混合减免：物理部分受物理穿透，魔法部分受法术穿透
                int half = originalDamage / 2;
                int defense = computePhysicalDefense(target);
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
        // 混合伤害：物理部分（力量×2+智力）的一半 + 魔法部分（智力×2+力量）的一半，统一暴击后相加
        if (type == AttackType.MIX_TYPE) {
            int physBase = computePhysicalAttack(entity) / 2;
            int magicBase = computeMagicalAttack(entity) / 2;
            double multiplier = rollCriticalMultiplier(entity);
            int fixedDmg = getAttributeValue(entity, DefaultAttributes.FIXED_DAMAGE_ID);
            return (int) (physBase * multiplier) + (int) (magicBase * multiplier) + fixedDmg;
        }

        // 根据攻击类型确定基础伤害（综合属性，动态计算）
        int baseDamage = switch (type) {
            case PHYSICAL -> computePhysicalAttack(entity);
            case MAGIC -> computeMagicalAttack(entity);
            default -> 0;
        };

        // 多层暴击判定
        double multiplier = rollCriticalMultiplier(entity);
        int fixedDmg = getAttributeValue(entity, DefaultAttributes.FIXED_DAMAGE_ID);
        return (int) (baseDamage * multiplier) + fixedDmg;
    }

    // ==================================================================
    // 综合属性（攻击力 / 防御力）派生公式
    // ==================================================================

    /**
     * 计算实体的物理攻击力（综合属性，动态派生）。
     * <p>
     * 玩家：由当前主职业的 {@link IProfession#computePhysicalAttack} 派生；
     * 非玩家或无职业模块：回退默认公式。
     * <p>
     * 力量/智力读取的是管线最终值（含装备/职业/属性点加成），故装备对一般属性的加成
     * 会自动计入攻击力。
     *
     * @param entity 攻击实体
     * @return 物理攻击力
     * @see ProfessionFormulas#physicalAttack
     */
    public static int computePhysicalAttack(LivingEntity entity) {
        int strength = getAttributeValue(entity, DefaultAttributes.STRENGTH_ID);
        int intelligence = getAttributeValue(entity, DefaultAttributes.INTELLIGENCE_ID);
        return ProfessionFormulas.physicalAttack(entity, strength, intelligence);
    }

    /**
     * 计算实体的魔法攻击力（综合属性，动态派生）。
     * <p>
     * 玩家：由当前主职业的 {@link IProfession#computeMagicalAttack} 派生；
     * 非玩家或无职业模块：回退默认公式。
     *
     * @param entity 攻击实体
     * @return 魔法攻击力
     * @see ProfessionFormulas#magicalAttack
     */
    public static int computeMagicalAttack(LivingEntity entity) {
        int strength = getAttributeValue(entity, DefaultAttributes.STRENGTH_ID);
        int intelligence = getAttributeValue(entity, DefaultAttributes.INTELLIGENCE_ID);
        return ProfessionFormulas.magicalAttack(entity, strength, intelligence);
    }

    /**
     * 计算实体的物理防御力（综合属性，动态派生）。
     * <p>
     * 玩家：由当前主职业的 {@link IProfession#computePhysicalDefense} 派生；
     * 非玩家或无职业模块：回退默认公式。
     * <p>
     * 魔法防御力不从此方法获得（魔法防御仅来自装备，无属性派生）。
     *
     * @param entity 防御实体
     * @return 物理防御力
     * @see ProfessionFormulas#physicalDefense
     */
    public static int computePhysicalDefense(LivingEntity entity) {
        int strength = getAttributeValue(entity, DefaultAttributes.STRENGTH_ID);
        int intelligence = getAttributeValue(entity, DefaultAttributes.INTELLIGENCE_ID);
        return ProfessionFormulas.physicalDefense(entity, strength, intelligence);
    }

    // ==================================================================
    // 暴击判定
    // ==================================================================

    /**
     * 多层暴击判定
     * <p>
     * <b>有效暴击率</b> = 暴击率属性 + 敏捷/5（每 5 点敏捷 +1 暴击率）；
     * <b>有效暴击伤害</b> = 暴击伤害属性 + (精准/5)×2（每 5 点精准 +2 暴击伤害）。
     * <p>
     * 暴击率每 100% 保底增加一层暴击伤害，超出部分作为下一层的触发概率。
     * <ul>
     *   <li>有效暴击率 50%  → 50% 概率 1 层暴击</li>
     *   <li>有效暴击率 120% → 保底 1 层 + 20% 概率第 2 层</li>
     *   <li>有效暴击率 300% → 保底 3 层暴击</li>
     * </ul>
     * 每层暴击将伤害乘以 {@code (1 + 有效暴击伤害/100.0)}，多层为幂次叠加。
     *
     * @param entity 攻击实体
     * @return 暴击倍率（≥1.0，1.0 表示未暴击）
     */
    private double rollCriticalMultiplier(LivingEntity entity) {
        // 有效暴击率 = 暴击率属性 + 敏捷派生（每 5 敏捷 +1）
        int critRateAttr = getAttributeValue(entity, DefaultAttributes.CRITICAL_RATE_ID);
        int agile = getAttributeValue(entity, DefaultAttributes.AGILE_ID);
        int effectiveCritRate = critRateAttr + agile / 5;
        if (effectiveCritRate <= 0) return 1.0;

        // 保底暴击层数 + 额外一层概率
        int fullCrits = effectiveCritRate / 100;
        int remainder = effectiveCritRate % 100;
        int totalCrits = fullCrits;

        if (remainder > 0 && ThreadLocalRandom.current().nextInt(100) < remainder) {
            totalCrits++;
        }

        if (totalCrits == 0) return 1.0;

        // 有效暴击伤害 = 暴击伤害属性 + 精准派生（每 5 精准 +2）
        int critRatioAttr = getAttributeValue(entity, DefaultAttributes.CRITICAL_RATIO_ID);
        int precision = getAttributeValue(entity, DefaultAttributes.PRECISION_ID);
        int effectiveCritRatio = critRatioAttr + (precision / 5) * 2;

        // 每层暴击乘以 (1 + 有效暴击伤害/100)，多层为幂次
        double critMulti = 1.0 + effectiveCritRatio / 100.0;
        return Math.pow(critMulti, totalCrits);
    }

    /**
     * 通过快照管理器读取实体属性值
     * <p>
     * 对非玩家实体：触发 {@code GatherAttributeEvent} 收集动态修饰符，
     * 通过 {@link com.rpgcraft.core.attribute.AttributePipeline} 计算最终值
     * （含装备/光环/职业加成），结果缓存在 Tick 级和跨 Tick 缓存中。
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
