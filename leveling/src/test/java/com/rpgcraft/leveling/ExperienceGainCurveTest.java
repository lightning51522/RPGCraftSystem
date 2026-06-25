package com.rpgcraft.leveling;

import com.rpgcraft.core.level.ExperienceGainCurve;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ExperienceGainCurve} 的纯数学单元测试。
 * <p>
 * 验证经验倍率曲线的所有锚点（甜区峰值、低级怪保底、高级怪封顶）及分段线性段的中点值。
 * 不依赖 Minecraft 运行时，零成本可在 CI 跑。
 */
@DisplayName("经验倍率曲线 ExperienceGainCurve")
class ExperienceGainCurveTest {

    // —— 甜区（|d| ≤ 5）——

    @Test
    @DisplayName("玩家与怪物同级时倍率为峰值 5.0")
    void sameLevel_returnsPeak() {
        assertEquals(5.0, ExperienceGainCurve.multiplier(10, 10), 1e-9);
        assertEquals(5.0, ExperienceGainCurve.multiplier(1, 1), 1e-9);
        assertEquals(5.0, ExperienceGainCurve.multiplier(100, 100), 1e-9);
    }

    @Test
    @DisplayName("玩家比怪高 5 级仍在甜区，倍率 5.0")
    void playerHigh5_returnsPeak() {
        assertEquals(5.0, ExperienceGainCurve.multiplier(15, 10), 1e-9);
    }

    @Test
    @DisplayName("玩家比怪低 5 级仍在甜区，倍率 5.0")
    void playerLow5_returnsPeak() {
        assertEquals(5.0, ExperienceGainCurve.multiplier(10, 15), 1e-9);
    }

    // —— 低级怪端（玩家高，d > 5）：5× → 0，在 d=20 处降到保底 ——

    @Test
    @DisplayName("玩家比怪高 12.5 级（甜区到保底的中点）倍率约 2.5")
    void playerHigh12_5_returnsMidpoint() {
        // d=12.5, t=(12.5-5)/(20-5)=0.5, M=5*(1-0.5)=2.5
        assertEquals(2.5, ExperienceGainCurve.multiplier(22, 10) * 1.0, 0.4);
    }

    @Test
    @DisplayName("玩家比怪高 20 级触发保底标记（multiplier 返回负值）")
    void playerHigh20_returnsFloorFlag() {
        assertTrue(ExperienceGainCurve.multiplier(30, 10) < ExperienceGainCurve.FLOOR_FLAG,
                "玩家高怪 ≥ LOW_MOB_GAP 应返回保底标记（负值）");
    }

    @Test
    @DisplayName("玩家比怪高 30 级仍触发保底标记")
    void playerHigh30_stillFloorFlag() {
        assertTrue(ExperienceGainCurve.multiplier(40, 10) < ExperienceGainCurve.FLOOR_FLAG);
    }

    // —— 高级怪端（玩家低，d < -5）：5× → 0.1×，在 d=-50 处降到 0.1×，之后恒 0.1× ——

    @Test
    @DisplayName("玩家比怪低 27.5 级（甜区到封顶的中点）倍率约 2.55")
    void playerLow27_5_returnsMidpoint() {
        // ad=27.5, t=(27.5-5)/(50-5)=0.5, M=5+(0.1-5)*0.5=2.55
        // 用整数等级近似：player=1, mob=29 → ad=28, t=23/45≈0.5111, M≈2.5
        double m = ExperienceGainCurve.multiplier(1, 29);
        assertTrue(m > 2.0 && m < 3.0, "中点附近倍率应在 2-3 之间，实际=" + m);
    }

    @Test
    @DisplayName("玩家比怪低 50 级倍率恰为封顶值 0.1")
    void playerLow50_returnsHighMobFloor() {
        assertEquals(0.1, ExperienceGainCurve.multiplier(1, 51), 1e-9);
    }

    @Test
    @DisplayName("玩家比怪低 80 级（远超封顶阈值）倍率仍恒为 0.1")
    void playerLow80_cappedAtHighMobFloor() {
        assertEquals(0.1, ExperienceGainCurve.multiplier(1, 81), 1e-9);
    }

    // —— gain() 端到端 ——

    @Nested
    @DisplayName("gain() 端到端经验计算")
    class GainTests {

        @Test
        @DisplayName("甜区内：baseExp=100，倍率 5× → 500 经验")
        void gain_inPeakZone() {
            assertEquals(500, ExperienceGainCurve.gain(10, 10, 100));
            assertEquals(500, ExperienceGainCurve.gain(15, 10, 100));
        }

        @Test
        @DisplayName("低级怪保底：玩家高怪 ≥ 20 级，无论 baseExp 多少都只得 1 点")
        void gain_lowMobFloor() {
            assertEquals(1, ExperienceGainCurve.gain(30, 10, 100));
            assertEquals(1, ExperienceGainCurve.gain(40, 10, 1000));
            assertEquals(1, ExperienceGainCurve.gain(50, 10, 100000));
        }

        @Test
        @DisplayName("高级怪封顶：玩家低怪 50 级，baseExp=100 → 倍率 0.1× → 10 经验")
        void gain_highMobFloor() {
            assertEquals(10, ExperienceGainCurve.gain(1, 51, 100));
        }

        @Test
        @DisplayName("高级怪封顶：玩家低怪 80 级仍恒 0.1× → 10 经验")
        void gain_highMobFloor_farGap() {
            assertEquals(10, ExperienceGainCurve.gain(1, 81, 100));
        }

        @Test
        @DisplayName("baseExp ≤ 0 时不给经验（返回 0）")
        void gain_zeroBaseExp() {
            assertEquals(0, ExperienceGainCurve.gain(10, 10, 0));
            assertEquals(0, ExperienceGainCurve.gain(10, 10, -5));
        }

        @Test
        @DisplayName("封顶保护侧：任何有效倍率下至少返回 1 点经验")
        void gain_atLeastOne() {
            // 高级怪封顶 0.1×，baseExp=5 → 0.5 → round → 1（Math.max 保底）
            assertEquals(1, ExperienceGainCurve.gain(1, 51, 5));
        }
    }

    // —— 鲁棒性 ——

    @Test
    @DisplayName("等级为 0 或负数时 clamp 到 1 不崩溃")
    void gain_clampsLevels() {
        // 等同于 player=1, mob=1（同级甜区）
        assertEquals(ExperienceGainCurve.gain(1, 1, 100),
                ExperienceGainCurve.gain(0, 0, 100));
        assertEquals(ExperienceGainCurve.gain(1, 1, 100),
                ExperienceGainCurve.gain(-5, -5, 100));
    }

    @Test
    @DisplayName("不对称性：高玩家低怪比低玩家高怪衰减更快")
    void gain_asymmetry() {
        // 同样 |d|=15：玩家高怪 15 级（d=+15，低级怪端中段）vs 玩家低怪 15 级（d=-15，高级怪端中段）
        // 低级怪端：d=15, t=10/15≈0.667, M=5*(1-0.667)≈1.67
        // 高级怪端：ad=15, t=10/45≈0.222, M=5+(0.1-5)*0.222≈3.92
        // 低级怪端衰减更多（值更小）
        double lowMobEnd = ExperienceGainCurve.multiplier(25, 10);   // d=+15
        double highMobEnd = ExperienceGainCurve.multiplier(10, 25);  // d=-15
        assertTrue(lowMobEnd < highMobEnd,
                "玩家高于怪时应衰减更快（倍率更低）: 低级怪端=" + lowMobEnd + ", 高级怪端=" + highMobEnd);
    }
}
