package com.rpgcraft.core.level.api;

/**
 * 经验倍率曲线管理门面 —— 持有可替换的 {@link IExperienceCurve} 静态引用。
 * <p>
 * 默认实例为 {@link DefaultExperienceCurve}（core 兜底）。
 * 子模组可通过 {@link #setCurve(IExperienceCurve)} 注入自定义曲线，
 * 这与 {@code AttributeManager.setDamageCalculator(IDamageCalculator)} 是同一可替换策略模式。
 * <p>
 * 查询方（如 {@code leveling} 模块的 {@code DefaultLevelCalculator}）通过
 * {@link #getCurve()} 获取当前生效的曲线，从而支持运行时替换而无需修改调用方代码。
 *
 * @apiNote 内部 API —— 第三方模组若需替换曲线，可直接调用 {@link #setCurve}，
 *          无需经 {@code RPGSystems} 门面（曲线属等级子系统内部关注点）。
 */
public final class ExperienceCurveManager {

    private static IExperienceCurve curve = new DefaultExperienceCurve();

    private ExperienceCurveManager() {
    }

    /**
     * 获取当前生效的经验倍率曲线。
     *
     * @return 曲线实例，永不为 null（默认为 {@link DefaultExperienceCurve}）
     */
    public static IExperienceCurve getCurve() {
        return curve;
    }

    /**
     * 替换经验倍率曲线实现。
     *
     * @param newCurve 新的曲线实例（不可为 null）
     * @throws NullPointerException {@code newCurve} 为 null 时
     */
    public static void setCurve(IExperienceCurve newCurve) {
        if (newCurve == null) {
            throw new NullPointerException("经验倍率曲线不可为 null");
        }
        curve = newCurve;
    }
}
