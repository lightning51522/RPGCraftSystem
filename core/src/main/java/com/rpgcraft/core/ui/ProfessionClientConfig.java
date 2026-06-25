package com.rpgcraft.core.ui;

/**
 * 职业模块配置的客户端镜像（仅客户端运行时调用）
 * <p>
 * 缓存从服务端接收的职业全局配置（副职业解锁消耗、默认等级上限、是否允许降级切换），
 * 供角色界面职业面板显示与可用性判断。服务端在玩家登录或 {@code /reload} 时通过
 * {@link com.rpgcraft.core.network.SyncProfessionConfigPacket} 推送。
 * <p>
 * 默认值与服务端 {@code ProfessionConfigLoader} 默认值一致：副职业解锁消耗 50000、
 * 默认等级上限 20、禁止降级切换；未收到同步包前按默认值处理，避免配置到达前显示错误。
 * <p>
 * 使用 {@code volatile} 保证网络线程写入和渲染线程读取之间的可见性。
 *
 * @see com.rpgcraft.core.network.SyncProfessionConfigPacket
 */
public final class ProfessionClientConfig {

    /** 副职业解锁消耗（默认 50000，与服务端一致） */
    private static volatile int secondaryUnlockCost = 50000;
    /** 默认等级上限（默认 20，与服务端一致） */
    private static volatile int defaultMaxLevel = 20;
    /** 是否允许从进阶职业降级切回基础（默认 false，与服务端一致） */
    private static volatile boolean allowDowngradeSwitch = false;

    private ProfessionClientConfig() {
        // 禁止实例化
    }

    /**
     * 设置全部配置值（由 {@link com.rpgcraft.core.network.SyncProfessionConfigPacket#handle} 在客户端主线程调用）。
     */
    public static void set(int secondaryUnlockCost, int defaultMaxLevel, boolean allowDowngradeSwitch) {
        ProfessionClientConfig.secondaryUnlockCost = secondaryUnlockCost;
        ProfessionClientConfig.defaultMaxLevel = defaultMaxLevel;
        ProfessionClientConfig.allowDowngradeSwitch = allowDowngradeSwitch;
    }

    public static int getSecondaryUnlockCost() {
        return secondaryUnlockCost;
    }

    public static int getDefaultMaxLevel() {
        return defaultMaxLevel;
    }

    public static boolean isAllowDowngradeSwitch() {
        return allowDowngradeSwitch;
    }

    /**
     * 重置为默认值（可在客户端断开连接等场景调用，避免上一次会话的配置残留）。
     */
    public static void reset() {
        secondaryUnlockCost = 50000;
        defaultMaxLevel = 20;
        allowDowngradeSwitch = false;
    }
}
