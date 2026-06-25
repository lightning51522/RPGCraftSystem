package com.rpgcraft.core.level.api;

import com.rpgcraft.core.level.ExperienceGainCurve;

/**
 * {@link IExperienceCurve} 的默认实现，委托到 {@link ExperienceGainCurve} 的分段线性公式。
 * <p>
 * 由 core 提供作为 {@link ExperienceCurveManager} 的兜底实现，保证<b>无 {@code leveling} 模块时
 * core 自身也能给出合理曲线</b>——与 {@code IDamageCalculator} 的透传兜底
 *（{@code AttributeManager.init()}）同一哲学。
 *
 * @see ExperienceGainCurve
 */
public class DefaultExperienceCurve implements IExperienceCurve {

    @Override
    public double multiplier(int playerLevel, int mobLevel) {
        return ExperienceGainCurve.multiplier(playerLevel, mobLevel);
    }
}
