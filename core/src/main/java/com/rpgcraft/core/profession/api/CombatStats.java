package com.rpgcraft.core.profession.api;

/**
 * 战斗属性快照 —— 所有可参与公式计算的一般属性值的聚合记录。
 * <p>
 * 供 {@link IProfession} 的公式方法（物理攻击/魔法攻击/防御/暴击派生等）
 * 一次性传入所有相关属性值，职业 override 时可自由选取任意属性参与计算。
 * <p>
 * 所有字段为管线最终值（含装备/职业/属性点加成），不可变 record。
 *
 * @param strength          力量
 * @param intelligence      智力
 * @param agile             敏捷
 * @param precision         精准
 * @param critRate          暴击率
 * @param critRatio         暴击伤害
 * @param fixedDamage       固定伤害
 * @param resistance        法抗
 * @param physicalPenetrate 物理穿透
 * @param magicalPenetrate  法术穿透
 */
public record CombatStats(
        int strength,
        int intelligence,
        int agile,
        int precision,
        int critRate,
        int critRatio,
        int fixedDamage,
        int resistance,
        int physicalPenetrate,
        int magicalPenetrate
) {
}
