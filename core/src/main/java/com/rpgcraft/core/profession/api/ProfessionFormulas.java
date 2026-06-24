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

    private static IProfession resolveMainProfession(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return null;
        if (!RPGSystems.hasProfessionSystem()) return null;
        return RPGSystems.getProfessionSystem().getProfession(player);
    }

    // ==================================================================
    // 综合属性查询（对外 API）
    // ==================================================================

    /** 物理攻击力 */
    public static int physicalAttack(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) return prof.computePhysicalAttack(s);
        return (int) Math.round(s.strength() * 2.0 + s.intelligence());
    }

    /** 魔法攻击力 */
    public static int magicalAttack(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) return prof.computeMagicalAttack(s);
        return (int) Math.round(s.intelligence() * 2.0 + s.strength());
    }

    /** 物理防御力 */
    public static int physicalDefense(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) return prof.computePhysicalDefense(s);
        return (int) Math.round(s.strength() * 2.0);
    }

    /** 有效暴击率 */
    public static int effectiveCritRate(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) return prof.computeEffectiveCritRate(s);
        return (int) Math.round(s.critRate() + s.agile() / 5.0);
    }

    /** 有效暴击伤害 */
    public static int effectiveCritDamage(LivingEntity entity, CombatStats s) {
        IProfession prof = resolveMainProfession(entity);
        if (prof != null) return prof.computeEffectiveCritDamage(s);
        return (int) Math.round(s.critRatio() + (s.precision() / 5.0) * 2);
    }
}
