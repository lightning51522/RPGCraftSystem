package com.rpgcraft.core.level.api;

import com.rpgcraft.core.level.ExpFormula;

/**
 * {@link IExpThresholdCurve} 的默认实现，委托到 {@link ExpFormula} 的 {@code round(50 × level^1.5)} 公式。
 * <p>
 * 由 core 提供作为 {@link ExpThresholdCurveManager} 的兜底实现，保证<b>无 {@code leveling} 模块时
 * core 自身也能给出合理曲线</b>——与 {@code DefaultExperienceCurve} 的兜底哲学一致。
 * <p>
 * 当第三方模组调用 {@link ExpThresholdCurveManager#setCurve} 注入自定义实现后，
 * 本实例将被替换；{@link ExpThresholdCurveManager#isOverridden()} 据此判断是否接管。
 *
 * @see ExpFormula
 * @see ExpThresholdCurveManager
 */
public class DefaultExpThresholdCurve implements IExpThresholdCurve {

    @Override
    public int expForNextLevel(int level) {
        return ExpFormula.expForNextLevel(level);
    }
}
