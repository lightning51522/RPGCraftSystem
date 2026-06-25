package com.rpgcraft.core.profession.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ProfessionFormulas} 默认公式的纯逻辑单元测试。
 * <p>
 * 锁定无职业时的综合属性派生值（与 {@link IProfession} 默认方法同源），防止未来改动漂移。
 * 不依赖 Minecraft 运行时。
 */
class ProfessionFormulasTest {

    private static CombatStats stats(int strength, int intelligence, int agile, int precision,
                                     int critRate, int critRatio) {
        return new CombatStats(strength, intelligence, agile, precision, critRate, critRatio,
                0, 0, 0, 0);
    }

    @Test
    void physicalAttack_isStrengthTimes2PlusIntelligence() {
        // 力量×2 + 智力
        CombatStats s = stats(10, 5, 0, 0, 0, 0);
        assertEquals(25, ProfessionFormulas.physicalAttack(s)); // 10*2 + 5
    }

    @Test
    void magicalAttack_isIntelligenceTimes2PlusStrength() {
        // 智力×2 + 力量
        CombatStats s = stats(5, 10, 0, 0, 0, 0);
        assertEquals(25, ProfessionFormulas.magicalAttack(s)); // 10*2 + 5
    }

    @Test
    void physicalDefense_isStrengthTimes2() {
        CombatStats s = stats(12, 0, 0, 0, 0, 0);
        assertEquals(24, ProfessionFormulas.physicalDefense(s)); // 12*2
    }

    @Test
    void effectiveCritRate_isCritRatePlusAgileDiv5() {
        // 暴击率 + 敏捷/5（浮点除法后四舍五入）
        CombatStats s = stats(0, 0, 13, 0, 7, 0);
        assertEquals(10, ProfessionFormulas.effectiveCritRate(s)); // 7 + 13/5.0(=2.6) = 9.6 → round 10
    }

    @Test
    void effectiveCritDamage_isCritRatioPlusPrecisionDiv5Times2() {
        // 暴击伤害 + (精准/5)*2（浮点除法后四舍五入）
        CombatStats s = stats(0, 0, 0, 12, 0, 50);
        assertEquals(55, ProfessionFormulas.effectiveCritDamage(s)); // 50 + (12/5.0=2.4)*2 = 54.8 → round 55
    }
}
