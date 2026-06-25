package com.rpgcraft.leveling;

import com.rpgcraft.core.level.ExpFormula;
import com.rpgcraft.core.level.api.DefaultExpThresholdCurve;
import com.rpgcraft.core.level.api.ExpThresholdCurveManager;
import com.rpgcraft.core.level.api.IExpThresholdCurve;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 玩家等级阈值经验曲线 SPI 的纯逻辑单元测试。
 * <p>
 * 覆盖：默认曲线与 {@link ExpFormula} 一致、{@link ExpThresholdCurveManager} 的
 * get/set/isOverridden 行为、自定义曲线注入后默认实例的识别。
 * <p>
 * 不依赖 Minecraft 运行时（仅 core 纯 Java 类），零成本可在 CI 跑。
 * {@code LevelConfig.loadFromJson} 的 JSON/SPI 优先级集成逻辑因依赖 MC 服务器上下文，
 * 由 {@code build} + 游戏内手动验证覆盖。
 */
@DisplayName("玩家等级阈值曲线 ExpThresholdCurveManager")
class ExpThresholdCurveTest {

    /** 每个测试后恢复默认曲线，避免测试间状态污染。 */
    @AfterEach
    void resetCurve() {
        ExpThresholdCurveManager.setCurve(new DefaultExpThresholdCurve());
    }

    @Test
    @DisplayName("默认曲线与 ExpFormula.expForNextLevel 完全一致")
    void defaultCurve_matchesExpFormula() {
        IExpThresholdCurve curve = ExpThresholdCurveManager.getCurve();
        // 抽查关键等级（与 ExpFormula.generateExpTable(300) 镜像值）
        assertEquals(ExpFormula.expForNextLevel(1), curve.expForNextLevel(1));     // 50
        assertEquals(ExpFormula.expForNextLevel(10), curve.expForNextLevel(10));   // 1581
        assertEquals(ExpFormula.expForNextLevel(50), curve.expForNextLevel(50));   // 17678
        assertEquals(ExpFormula.expForNextLevel(100), curve.expForNextLevel(100)); // 50000
        assertEquals(ExpFormula.expForNextLevel(299), curve.expForNextLevel(299)); // 258510
    }

    @Test
    @DisplayName("默认曲线锚点值正确（1→2 需 50，10→11 需 1581，100→101 需 50000）")
    void defaultCurve_anchorValues() {
        IExpThresholdCurve curve = ExpThresholdCurveManager.getCurve();
        assertEquals(50, curve.expForNextLevel(1));
        assertEquals(1581, curve.expForNextLevel(10));
        assertEquals(50000, curve.expForNextLevel(100));
    }

    @Test
    @DisplayName("level < 1 时返回 -1（视为不可升级/已满级，统一 sentinel 约定）")
    void defaultCurve_invalidLevel() {
        IExpThresholdCurve curve = ExpThresholdCurveManager.getCurve();
        assertEquals(-1, curve.expForNextLevel(0));
        assertEquals(-1, curve.expForNextLevel(-1));
    }

    @Test
    @DisplayName("未注册自定义曲线时 isOverridden() 为 false")
    void notOverridden_byDefault() {
        assertFalse(ExpThresholdCurveManager.isOverridden(),
                "默认（DefaultExpThresholdCurve）不应视为 overridden");
    }

    @Test
    @DisplayName("注册自定义曲线后 isOverridden() 为 true 且 getCurve 返回新实现")
    void overridden_afterSetCustomCurve() {
        IExpThresholdCurve custom = level -> level * 100; // 固定 100×level
        ExpThresholdCurveManager.setCurve(custom);

        assertTrue(ExpThresholdCurveManager.isOverridden(), "注册自定义曲线后应标记 overridden");
        assertSame(custom, ExpThresholdCurveManager.getCurve(), "getCurve 应返回新实现");
        assertEquals(500, ExpThresholdCurveManager.getCurve().expForNextLevel(5));
    }

    @Test
    @DisplayName("重新注册回 DefaultExpThresholdCurve 后 isOverridden() 恢复 false")
    void overridden_resetsWhenDefaultRestored() {
        // 先注册自定义
        ExpThresholdCurveManager.setCurve(level -> level * 100);
        assertTrue(ExpThresholdCurveManager.isOverridden());

        // 恢复默认
        ExpThresholdCurveManager.setCurve(new DefaultExpThresholdCurve());
        assertFalse(ExpThresholdCurveManager.isOverridden(),
                "恢复 DefaultExpThresholdCurve 后 isOverridden 应回到 false（JSON 重新生效）");
    }

    @Test
    @DisplayName("setCurve(null) 抛出 NullPointerException")
    void setCurve_null_throws() {
        assertThrows(NullPointerException.class, () -> ExpThresholdCurveManager.setCurve(null));
    }

    @Test
    @DisplayName("DefaultExpThresholdCurve 子类实例仍被视为默认（不标记 overridden）")
    void defaultCurve_subclass_treatedAsDefault() {
        // 关键：isOverridden 用 instanceof DefaultExpThresholdCurve 判定。
        // 这保证只有"真正不同的实现"才接管；子类化默认实现不会误触发 JSON 失效。
        class Sub extends DefaultExpThresholdCurve {}
        ExpThresholdCurveManager.setCurve(new Sub());
        assertFalse(ExpThresholdCurveManager.isOverridden(),
                "DefaultExpThresholdCurve 的子类应视为默认（不接管 JSON）");
    }
}
