package com.rpgcraft.core.profession.api;

import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 综合属性派生公式解析工具（核心逻辑，跨模块复用）。
 * <p>
 * 封装「解出实体的活跃主职业 → 调用职业公式 → 回退默认公式」的解析链。
 * 调用方负责从实体读取属性值构建 {@link CombatStats}，本工具仅负责职业解析和回退。
 * <p>
 * <b>解析规则：</b>
 * <ul>
 *   <li>实体为 {@link ServerPlayer} 且职业模块已注册 → 调用当前主职业的
 *       {@link IProfession#computePhysicalAttack(CombatStats) CombatStats} 系列方法</li>
 *   <li>实体非玩家 或 职业模块未加载 → 回退到默认公式</li>
 * </ul>
 *
 * @see CombatStats
 * @see IProfession#computePhysicalAttack(CombatStats)
 */
public final class ProfessionFormulas {

    private ProfessionFormulas() {
    }

    /**
     * 全默认 {@link IProfession} 实例（仅用接口 {@code compute*} 默认方法）。
     * <p>
     * 作为「无职业时的默认公式」的单一真相源：所有默认公式集中在 {@link IProfession} 的
     * {@code compute*} 默认方法中，本类不再重复内联公式，避免三处拷贝漂移
     *（服务端 {@code DefaultDamageCalculator}、客户端 {@code CompositeAttributePlugin}）。
     * <p>
     * 三个抽象方法（getId/getDisplayName/getDescription）返回占位值——本实例<b>仅</b>用于
     * 调用 {@code compute*} 默认方法，从不作为真实职业参与注册/查询。
     */
    private static final IProfession DEFAULTS = new IProfession() {
        @Override
        public net.minecraft.resources.Identifier getId() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return "";
        }

        @Override
        public String getDescription() {
            return "";
        }
    };

    private static IProfession resolveMainProfession(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return null;
        if (!RPGSystems.hasProfessionSystem()) return null;
        return RPGSystems.getProfessionSystem().getProfession(player);
    }

    // ==================================================================
    // 默认公式（无职业时的综合属性，单一真相源 = IProfession 默认方法）
    // ==================================================================

    /**
     * 默认物理攻击力（无职业时），委托 {@link IProfession#computePhysicalAttack(CombatStats)} 默认实现。
     * <p>
     * 供客户端（{@code CompositeAttributePlugin}）等无实体上下文的场景直接复用，消除内联公式拷贝。
     */
    public static int physicalAttack(CombatStats s) {
        return DEFAULTS.computePhysicalAttack(s);
    }

    /** 默认魔法攻击力（无职业时），委托 {@link IProfession#computeMagicalAttack(CombatStats)}。 */
    public static int magicalAttack(CombatStats s) {
        return DEFAULTS.computeMagicalAttack(s);
    }

    /** 默认物理防御力（无职业时），委托 {@link IProfession#computePhysicalDefense(CombatStats)}。 */
    public static int physicalDefense(CombatStats s) {
        return DEFAULTS.computePhysicalDefense(s);
    }

    /** 默认有效暴击率（无职业时），委托 {@link IProfession#computeEffectiveCritRate(CombatStats)}。 */
    public static int effectiveCritRate(CombatStats s) {
        return DEFAULTS.computeEffectiveCritRate(s);
    }

    /** 默认有效暴击伤害（无职业时），委托 {@link IProfession#computeEffectiveCritDamage(CombatStats)}。 */
    public static int effectiveCritDamage(CombatStats s) {
        return DEFAULTS.computeEffectiveCritDamage(s);
    }

    // ==================================================================
    // 综合属性查询（带实体的对外 API，解析活跃主职业并回退默认）
    // ==================================================================

    /** 物理攻击力 */
    public static int physicalAttack(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        return prof != null ? prof.computePhysicalAttack(s) : physicalAttack(s);
    }

    /** 魔法攻击力 */
    public static int magicalAttack(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        return prof != null ? prof.computeMagicalAttack(s) : magicalAttack(s);
    }

    /** 物理防御力 */
    public static int physicalDefense(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        return prof != null ? prof.computePhysicalDefense(s) : physicalDefense(s);
    }

    /** 有效暴击率 */
    public static int effectiveCritRate(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        return prof != null ? prof.computeEffectiveCritRate(s) : effectiveCritRate(s);
    }

    /** 有效暴击伤害 */
    public static int effectiveCritDamage(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        return prof != null ? prof.computeEffectiveCritDamage(s) : effectiveCritDamage(s);
    }
}
