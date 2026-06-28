package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.EquipmentRarity;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 装备稀有度动态化的单元测试。
 * <p>
 * 覆盖：{@link EquipmentRarity} 的 getTier/getBonusMultiplier、{@link EquipmentRarityConfig} 的
 * 概率查询与 rollRarity 分布。通过直接写入 {@link EquipmentRarityConfig} 的包私有 {@code probabilities}
 * 字段模拟 reload 后的 apply 状态（与真实流程等价），不依赖 Minecraft 服务器运行时。
 * <p>
 * {@link RandomSource#create()} 可在无服务器环境下构造（ThreadSafe 实现）。
 */
class EquipmentRarityTest {

    @AfterEach
    void resetProbabilities() {
        // 恢复概率表为空（= 全部 0），避免污染其它测试
        EquipmentRarityConfig.probabilities = new EnumMap<>(EquipmentRarity.class);
    }

    private void setProbabilities(Map<EquipmentRarity, Double> map) {
        // EnumMap 复制构造器拒绝空 Map，故显式构造再 putAll
        EnumMap<EquipmentRarity, Double> target = new EnumMap<>(EquipmentRarity.class);
        target.putAll(map);
        EquipmentRarityConfig.probabilities = target;
    }

    // ----------------------------------------------------------------
    // EquipmentRarity：tier 与 multiplier
    // ----------------------------------------------------------------

    @Test
    void tier_isOrdinalFromZero() {
        assertEquals(0, EquipmentRarity.GRAY.getTier());
        assertEquals(1, EquipmentRarity.WHITE.getTier());
        assertEquals(9, EquipmentRarity.RAINBOW.getTier());
    }

    @Test
    void multiplier_isOnePlusPointOnePerTier() {
        // GRAY=1.0×，每升一级 +0.1
        assertEquals(1.0, EquipmentRarity.GRAY.getBonusMultiplier(), 1e-9);
        assertEquals(1.1, EquipmentRarity.WHITE.getBonusMultiplier(), 1e-9);
        assertEquals(1.5, EquipmentRarity.ORANGE.getBonusMultiplier(), 1e-9);
        assertEquals(1.9, EquipmentRarity.RAINBOW.getBonusMultiplier(), 1e-9);
    }

    @Test
    void floorScalingMatchesExpectedValues() {
        // 验证 +10%/级 在整数加成上的效果（floor）
        // 基础 10：GRAY=10、WHITE=11、ORANGE(×1.5)=15、RAINBOW(×1.9)=19
        assertEquals(10, (int) Math.floor(10 * EquipmentRarity.GRAY.getBonusMultiplier()));
        assertEquals(11, (int) Math.floor(10 * EquipmentRarity.WHITE.getBonusMultiplier()));
        assertEquals(15, (int) Math.floor(10 * EquipmentRarity.ORANGE.getBonusMultiplier()));
        assertEquals(19, (int) Math.floor(10 * EquipmentRarity.RAINBOW.getBonusMultiplier()));
    }

    @Test
    void fromName_caseInsensitiveAndFallback() {
        assertEquals(EquipmentRarity.GREEN, EquipmentRarity.fromName("green"));
        assertEquals(EquipmentRarity.GREEN, EquipmentRarity.fromName("GREEN"));
        assertEquals(EquipmentRarity.RAINBOW, EquipmentRarity.fromName("rainbow"));
        assertEquals(EquipmentRarity.GRAY, EquipmentRarity.fromName("nonexistent"));
        assertEquals(EquipmentRarity.GRAY, EquipmentRarity.fromName(null));
    }

    // ----------------------------------------------------------------
    // EquipmentRarityConfig：概率查询
    // ----------------------------------------------------------------

    @Test
    void probability_grayAlwaysZero() {
        // 即使误把 GRAY 写进概率表（实际 applyConfig 会拒绝），getProbability 仍强制返回 0
        Map<EquipmentRarity, Double> map = new HashMap<>();
        map.put(EquipmentRarity.GRAY, 0.5);
        setProbabilities(map);
        assertEquals(0.0, EquipmentRarityConfig.getProbability(EquipmentRarity.GRAY));
    }

    @Test
    void probability_defaultsToZeroWhenUnconfigured() {
        assertEquals(0.0, EquipmentRarityConfig.getProbability(EquipmentRarity.BLUE));
    }

    @Test
    void probability_readsConfiguredValue() {
        Map<EquipmentRarity, Double> map = new HashMap<>();
        map.put(EquipmentRarity.BLUE, 0.10);
        setProbabilities(map);
        assertEquals(0.10, EquipmentRarityConfig.getProbability(EquipmentRarity.BLUE));
    }

    // ----------------------------------------------------------------
    // EquipmentRarityConfig.rollRarity：分布
    // ----------------------------------------------------------------

    @Test
    void rollRarity_allZeroProbabilitiesAlwaysGray() {
        setProbabilities(new HashMap<>()); // 全部概率 0
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(EquipmentRarity.GRAY, EquipmentRarityConfig.rollRarity(random));
        }
    }

    @Test
    void rollRarity_certaintyReturnsThatTier() {
        // WHITE 概率 1.0 → 任何 r∈[0,1) 都 < 1.0 → 必命中 WHITE
        Map<EquipmentRarity, Double> map = new HashMap<>();
        map.put(EquipmentRarity.WHITE, 1.0);
        setProbabilities(map);
        RandomSource random = RandomSource.create(1L);
        for (int i = 0; i < 100; i++) {
            assertEquals(EquipmentRarity.WHITE, EquipmentRarityConfig.rollRarity(random));
        }
    }

    @Test
    void rollRarity_higherTierBeatsLowerOnSameRoll() {
        // WHITE=0.5、GREEN=0.5：从高到低遍历，GREEN 先判定。
        // r < 0.5 → 命中 GREEN；否则跳过 GREEN，再判 WHITE（r<0.5 已不成立）→ 落到 GRAY
        // 故期望：约 50% GREEN，约 50% GRAY，几乎不出现 WHITE
        Map<EquipmentRarity, Double> map = new HashMap<>();
        map.put(EquipmentRarity.WHITE, 0.5);
        map.put(EquipmentRarity.GREEN, 0.5);
        setProbabilities(map);

        RandomSource random = RandomSource.create(7L);
        int green = 0, white = 0, gray = 0;
        int n = 20000;
        for (int i = 0; i < n; i++) {
            EquipmentRarity r = EquipmentRarityConfig.rollRarity(random);
            switch (r) {
                case GREEN -> green++;
                case WHITE -> white++;
                case GRAY -> gray++;
                default -> { /* 其它等级概率 0，不应出现 */ }
            }
        }
        // GREEN ≈ 50%，GRAY ≈ 50%，WHITE 接近 0
        assertTrue(green > n * 0.45 && green < n * 0.55, "GREEN 应约 50%，实际 " + green);
        assertTrue(gray > n * 0.45 && gray < n * 0.55, "GRAY 应约 50%，实际 " + gray);
        assertTrue(white < n * 0.02, "WHITE 应极少（同 roll 被 GREEN 抢占），实际 " + white);
    }

    @Test
    void rollRarity_defaultTemplateProducesReasonableDistribution() {
        // 用模板默认概率表跑大批样本，校验：GRAY 占比最高，RAINBOD 极少，无越界等级
        Map<EquipmentRarity, Double> map = new HashMap<>();
        map.put(EquipmentRarity.WHITE, 0.40);
        map.put(EquipmentRarity.GREEN, 0.20);
        map.put(EquipmentRarity.BLUE, 0.10);
        map.put(EquipmentRarity.PURPLE, 0.05);
        map.put(EquipmentRarity.ORANGE, 0.03);
        map.put(EquipmentRarity.PINK, 0.02);
        map.put(EquipmentRarity.GOLD, 0.01);
        map.put(EquipmentRarity.RED, 0.005);
        map.put(EquipmentRarity.RAINBOW, 0.001);
        setProbabilities(map);

        RandomSource random = RandomSource.create(123L);
        Map<EquipmentRarity, Integer> counts = new EnumMap<>(EquipmentRarity.class);
        int n = 50000;
        for (int i = 0; i < n; i++) {
            EquipmentRarity r = EquipmentRarityConfig.rollRarity(random);
            counts.merge(r, 1, Integer::sum);
        }
        // GRAY 是兜底，应占比最高（约 1 - 0.4 = 0.6，因 WHITE 先判 0.4）
        int gray = counts.getOrDefault(EquipmentRarity.GRAY, 0);
        assertTrue(gray > n * 0.5, "GRAY 应占多数，实际 " + gray + "/" + n);
        // RAINBOD 极少（概率 0.001，且被更高优先级压制，实际 < 0.1%）
        int rainbow = counts.getOrDefault(EquipmentRarity.RAINBOW, 0);
        assertTrue(rainbow < n * 0.005, "RAINBOW 应极少，实际 " + rainbow + "/" + n);
    }
}
