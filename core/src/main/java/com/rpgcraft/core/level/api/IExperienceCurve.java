package com.rpgcraft.core.level.api;

import com.rpgcraft.core.level.ExperienceGainCurve;

/**
 * 等级差经验倍率曲线策略接口。
 * <p>
 * 定义"玩家等级 vs 怪物等级"差值如何映射到经验倍率。默认实现由 core 提供
 *（{@link DefaultExperienceCurve}，委托到 {@link ExperienceGainCurve} 的分段线性公式）。
 * <p>
 * 其他模组可替换此实现来提供完全不同的曲线（如考虑职业、地图、时间等因素），
 * 通过 {@link ExperienceCurveManager#setCurve(IExperienceCurve)} 注入；
 * 这与 {@code IDamageCalculator} / {@code AttributeManager.setDamageCalculator} 是同一可替换策略模式。
 * <p>
 * 与 {@link ILevelCalculator} 的关系：{@code ILevelCalculator} 是<b>整体</b>经验计算器
 *（含属性加成、保底等完整逻辑），而 {@code IExperienceCurve} 只负责<b>等级差倍率</b>这一子环节，
 * 便于细粒度替换而无需重写整个计算器。
 *
 * @see ExperienceGainCurve
 * @see ExperienceCurveManager
 */
public interface IExperienceCurve {

    /**
     * 计算等级差倍率 {@code M(playerLevel, mobLevel)}。
     * <p>
     * 返回值约定（与 {@link ExperienceGainCurve#multiplier} 一致）：
     * <ul>
     *   <li>正值：实际倍率</li>
     *   <li>负值（{@code < 0}）：走低级怪保底逻辑（每次击杀至少 1 点经验）</li>
     * </ul>
     * 调用方一般通过 {@link ExperienceGainCurve#gain} 或 {@link DefaultExperienceCurve} 间接消费，
     * 而非自行解析返回值。
     *
     * @param playerLevel 玩家等级（实现内部应 clamp 到 ≥ 1）
     * @param mobLevel    怪物等级（实现内部应 clamp 到 ≥ 1）
     * @return 倍率，或负值表示走保底逻辑
     */
    double multiplier(int playerLevel, int mobLevel);
}
