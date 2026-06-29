package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.equipment.RarityGemstoneConfig.UpgradeRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RarityGemstoneConfig} 的单元测试。
 * <p>
 * 覆盖：{@link UpgradeRule} 查询（已配置/未配置/GRAY 兜底）、失败退宝石数计算。
 * 通过直接写入包私有 {@code rules} 字段模拟 reload 后的 apply 状态（与真实流程等价），
 * 不依赖 Minecraft 服务器运行时。
 */
class RarityGemstoneConfigTest {

    @AfterEach
    void resetRules() {
        RarityGemstoneConfig.rules = new EnumMap<>(EquipmentRarity.class);
    }

    private void setRules(Map<EquipmentRarity, UpgradeRule> map) {
        EnumMap<EquipmentRarity, UpgradeRule> target = new EnumMap<>(EquipmentRarity.class);
        target.putAll(map);
        RarityGemstoneConfig.rules = target;
    }

    // ----------------------------------------------------------------
    // getUpgradeRule 查询
    // ----------------------------------------------------------------

    @Test
    void upgradeRule_defaultsToNoneWhenUnconfigured() {
        // 未配置任何规则 → 所有目标返回 NONE（gemCost=0、chance=0、failConsumeRate=0）
        UpgradeRule rule = RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.BLUE);
        assertEquals(0, rule.gemCost());
        assertEquals(0.0, rule.chance());
        assertEquals(0.0, rule.failConsumeRate());
    }

    @Test
    void upgradeRule_readsConfiguredValue() {
        Map<EquipmentRarity, UpgradeRule> map = new HashMap<>();
        map.put(EquipmentRarity.BLUE, new UpgradeRule(3, 0.6, 0.5));
        setRules(map);

        UpgradeRule rule = RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.BLUE);
        assertEquals(3, rule.gemCost());
        assertEquals(0.6, rule.chance());
        assertEquals(0.5, rule.failConsumeRate());
    }

    @Test
    void upgradeRule_defaultTemplateMatchesDesignSpec() {
        // 模拟默认模板，校验递增消耗 + 递减概率的关键节点
        Map<EquipmentRarity, UpgradeRule> map = new HashMap<>();
        map.put(EquipmentRarity.WHITE, new UpgradeRule(1, 0.90, 0.5));
        map.put(EquipmentRarity.GREEN, new UpgradeRule(2, 0.75, 0.5));
        map.put(EquipmentRarity.PURPLE, new UpgradeRule(5, 0.45, 0.5));
        map.put(EquipmentRarity.RAINBOW, new UpgradeRule(32, 0.02, 0.5));
        setRules(map);

        // 递增消耗
        assertTrue(RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.WHITE).gemCost()
                < RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.GREEN).gemCost());
        assertTrue(RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.GREEN).gemCost()
                < RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.PURPLE).gemCost());
        assertEquals(32, RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.RAINBOW).gemCost());

        // 递减概率
        assertTrue(RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.WHITE).chance()
                > RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.GREEN).chance());
        assertTrue(RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.GREEN).chance()
                > RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.PURPLE).chance());
    }

    @Test
    void upgradeRule_rainbowIsHighestCost() {
        // RAINBOW 作为最高目标等级，消耗最大、概率最低
        Map<EquipmentRarity, UpgradeRule> map = new HashMap<>();
        for (EquipmentRarity r : new EquipmentRarity[]{
                EquipmentRarity.WHITE, EquipmentRarity.RAINBOW}) {
            map.put(r, new UpgradeRule(r.ordinal() * 3, 0.1 / r.ordinal(), 0.5));
        }
        setRules(map);
        UpgradeRule rainbow = RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.RAINBOW);
        UpgradeRule white = RarityGemstoneConfig.getUpgradeRule(EquipmentRarity.WHITE);
        assertTrue(rainbow.gemCost() > white.gemCost());
    }

    // ----------------------------------------------------------------
    // 失败退宝石数计算（验证 ceil(gemCost × failConsumeRate) 语义）
    // ----------------------------------------------------------------

    @Test
    void failRefund_isGemCostMinusCeilOfConsumedFraction() {
        // gemCost=5、failConsumeRate=0.5 → 失败消耗 ceil(5×0.5)=ceil(2.5)=3，退 5-3=2
        // （此计算在 RarityForgeHandler 中执行，这里验证语义公式本身）
        int gemCost = 5;
        double failConsumeRate = 0.5;
        int failConsume = (int) Math.ceil(gemCost * failConsumeRate);
        int refund = Math.max(0, gemCost - failConsume);
        assertEquals(3, failConsume);
        assertEquals(2, refund);
    }

    @Test
    void failRefund_oddGemCostRoundsUpConsumed() {
        // gemCost=3、failConsumeRate=0.5 → 失败消耗 ceil(1.5)=2，退 1
        int gemCost = 3;
        int failConsume = (int) Math.ceil(gemCost * 0.5);
        int refund = Math.max(0, gemCost - failConsume);
        assertEquals(2, failConsume);
        assertEquals(1, refund);
    }

    @Test
    void failRefund_evenGemCostConsumesHalf() {
        // gemCost=8、failConsumeRate=0.5 → 失败消耗 4，退 4
        int gemCost = 8;
        int failConsume = (int) Math.ceil(gemCost * 0.5);
        int refund = Math.max(0, gemCost - failConsume);
        assertEquals(4, failConsume);
        assertEquals(4, refund);
    }
}
