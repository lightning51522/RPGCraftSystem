package com.rpgcraft.core.level.api;

/**
 * 玩家等级阈值曲线管理门面 —— 持有可替换的 {@link IExpThresholdCurve} 静态引用。
 * <p>
 * 默认实例为 {@link DefaultExpThresholdCurve}（core 兜底，委托 {@code ExpFormula}）。
 * 子模组（如 {@code attributes} 模块）可通过 {@link #setCurve(IExpThresholdCurve)} 注入自定义曲线，
 * 这与 {@code ExperienceCurveManager} / {@code AttributeManager.setDamageCalculator} 是同一可替换策略模式。
 * <p>
 * 查询方（如 {@code leveling} 的 {@code LevelConfig}、{@code client} 的职业面板预览）
 * 通过 {@link #getCurve()} 获取当前生效的曲线。
 *
 * <h2>与 JSON 的优先级</h2>
 * 一旦注册了自定义曲线（非 core 默认实现），{@link #isOverridden()} 返回 {@code true}，
 * {@code LevelConfig.loadFromJson} 会据此忽略 {@code level_config.json} 并改用 SPI 曲线重建表。
 * 这保证 SPI（公式级覆盖）优先于 JSON（数据级微调）。
 *
 * @apiNote 内部 API —— 第三方模组若需替换曲线，可直接调用 {@link #setCurve}，
 *          无需经 {@code RPGSystems} 门面（曲线属等级子系统内部关注点）。
 * @see IExpThresholdCurve
 * @see DefaultExpThresholdCurve
 */
public final class ExpThresholdCurveManager {

    /** core 默认实例（单例，用于判断 setCurve 是否真正覆盖）。 */
    private static final IExpThresholdCurve DEFAULT = new DefaultExpThresholdCurve();

    private static IExpThresholdCurve curve = DEFAULT;
    private static boolean overridden = false;

    private ExpThresholdCurveManager() {
    }

    /**
     * 获取当前生效的玩家等级阈值曲线。
     *
     * @return 曲线实例，永不为 null（默认为 {@link DefaultExpThresholdCurve}）
     */
    public static IExpThresholdCurve getCurve() {
        return curve;
    }

    /**
     * 替换玩家等级阈值曲线。
     * <p>
     * 若传入的不是 core 默认实现，则标记为 overridden（{@link #isOverridden()} 返回 {@code true}），
     * 并通过 {@link LevelConfigHooks} 通知 leveling 模块重建已生成的经验表（忽略 JSON）。
     *
     * @param newCurve 新的曲线实例（不可为 null）
     * @throws NullPointerException {@code newCurve} 为 null 时
     */
    public static void setCurve(IExpThresholdCurve newCurve) {
        if (newCurve == null) {
            throw new NullPointerException("玩家等级阈值曲线不可为 null");
        }
        curve = newCurve;
        overridden = !(newCurve instanceof DefaultExpThresholdCurve);
        // 通知 leveling 重建经验表（若已实例化）。SPI 优先于 JSON。
        LevelConfigHooks.onThresholdCurveChanged();
    }

    /**
     * 是否已注册自定义曲线（非 core 默认实现）。
     * <p>
     * {@code LevelConfig} 据此判断是否忽略 {@code level_config.json} 并改用 SPI 曲线重建表。
     *
     * @return {@code true} 表示自定义曲线已接管，JSON 应被忽略
     */
    public static boolean isOverridden() {
        return overridden;
    }
}
