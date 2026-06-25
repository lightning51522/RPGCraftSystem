package com.rpgcraft.core.level.api;

/**
 * core → leveling 的轻量回调桥，避免 core 反向依赖 {@code leveling} 模块。
 * <p>
 * 玩家等级阈值表实现在 {@code leveling} 的 {@code LevelConfig} 中，但 SPI 管理器
 * {@link ExpThresholdCurveManager} 在 core 中。当第三方模组注册自定义曲线
 * （{@link ExpThresholdCurveManager#setCurve}）时，需通知 leveling 重建已生成的经验表
 * （SPI 优先于 JSON）。core 不能直接 import leveling 类，故通过此函数式回调桥解耦。
 * <p>
 * 注册时机：{@code leveling} 模块的 {@code LevelManager.init()} 中调用
 * {@link #setOnThresholdCurveChanged(Runnable)} 注册 {@code LevelConfig::rebuildFromCurve}。
 *
 * @apiNote 内部 API —— 仅供 {@code leveling} 模块注册和 {@link ExpThresholdCurveManager} 调用。
 */
public final class LevelConfigHooks {

    private LevelConfigHooks() {
    }

    /** 曲线变更回调；为 null 时表示 leveling 尚未初始化（此时表尚未生成，无需重建）。 */
    private static Runnable onThresholdCurveChanged;

    /**
     * 注册曲线变更回调（由 {@code leveling} 模块在初始化时调用）。
     *
     * @param callback 曲线变更时应执行的操作（通常为 {@code LevelConfig::rebuildFromCurve}）
     */
    public static void setOnThresholdCurveChanged(Runnable callback) {
        onThresholdCurveChanged = callback;
    }

    /**
     * 触发已注册的曲线变更回调；未注册时静默跳过。
     * <p>
     * 由 {@link ExpThresholdCurveManager#setCurve} 在曲线被替换后调用。
     */
    static void onThresholdCurveChanged() {
        if (onThresholdCurveChanged != null) {
            onThresholdCurveChanged.run();
        }
    }
}
