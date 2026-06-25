package com.rpgcraft.core.level.api;

import com.rpgcraft.core.level.ExpFormula;

/**
 * 玩家等级阈值经验曲线策略接口 —— 从某等级升到下一级所需经验。
 * <p>
 * 默认实现由 core 提供（{@link DefaultExpThresholdCurve}，委托到 {@link ExpFormula} 的
 * {@code round(50 × level^1.5)} 公式）。
 * <p>
 * 其他模组（如 {@code attributes} 模块）可替换此实现来提供完全不同的升级经验曲线，
 * 通过 {@link ExpThresholdCurveManager#setCurve(IExpThresholdCurve)} 注入；
 * 这与 {@code IExperienceCurve} / {@code ExperienceCurveManager} 是同一可替换策略模式。
 * <p>
 * <b>作用范围</b>：仅影响<b>玩家等级</b>（{@code leveling} 模块）的升级阈值表。
 * 职业等级（{@code profession} 模块）仍直调 {@link ExpFormula}，不受本 SPI 影响。
 * <p>
 * <b>与 JSON 的优先级</b>：当注册了自定义曲线（非 core 默认实现）后，
 * {@code data/rpgcraftcore/rpg/level_config.json} 将被忽略（由 {@code LevelConfig} 判定，
 * 见 {@link ExpThresholdCurveManager#isOverridden()}）；未注册自定义曲线时 JSON 行为不变。
 *
 * @see ExpFormula
 * @see ExpThresholdCurveManager
 * @see IExperienceCurve
 */
public interface IExpThresholdCurve {

    /**
     * 计算从 {@code level} 升到 {@code level+1} 所需的经验。
     *
     * @param level 当前等级（≥ 1）
     * @return 升级所需经验；{@code level < 1} 返回 {@link Integer#MAX_VALUE}（视为不可升级）
     */
    int expForNextLevel(int level);
}
