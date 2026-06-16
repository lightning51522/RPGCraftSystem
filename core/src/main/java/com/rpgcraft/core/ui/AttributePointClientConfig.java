package com.rpgcraft.core.ui;

/**
 * 属性点模块配置的客户端镜像（仅客户端运行时调用）
 * <p>
 * 缓存从服务端接收的 {@code allow_decrease} 配置值，供角色界面的属性点面板
 * 决定是否渲染/响应 {@code [-]} 按钮。服务端在玩家登录或 {@code /reload} 时通过
 * {@link com.rpgcraft.core.network.SyncAttributePointsConfigPacket} 推送。
 * <p>
 * 默认值为 {@code true}（允许减少），与服务端代码默认值一致；未收到同步包前按允许处理，
 * 避免在配置尚未到达时错误地隐藏按钮。
 * <p>
 * 使用 {@code volatile} 保证网络线程写入和渲染线程读取之间的可见性。
 * 写入操作通过 {@code IPayloadContext.enqueueWork()} 已投递到客户端主线程，
 * {@code volatile} 是额外的安全保障。
 *
 * @see com.rpgcraft.core.network.SyncAttributePointsConfigPacket
 */
public final class AttributePointClientConfig {

    /** 是否允许减少属性点（volatile 保证多线程可见性） */
    private static volatile boolean allowDecrease = true;

    private AttributePointClientConfig() {
        // 禁止实例化
    }

    /**
     * 设置配置值
     * <p>
     * 由 {@link com.rpgcraft.core.network.SyncAttributePointsConfigPacket#handle} 在客户端主线程调用。
     *
     * @param allowDecrease 是否允许减少属性点
     */
    public static void set(boolean allowDecrease) {
        AttributePointClientConfig.allowDecrease = allowDecrease;
    }

    /**
     * 查询是否允许减少属性点
     * <p>
     * 由角色界面属性点面板在每帧渲染/鼠标点击时调用。
     *
     * @return {@code true} 允许减少（渲染并响应 {@code [-]} 按钮）；{@code false} 禁止
     */
    public static boolean isAllowDecrease() {
        return allowDecrease;
    }

    /**
     * 重置为默认值（允许减少）
     * <p>
     * 可在客户端断开连接等场景调用，避免上一次会话的配置残留。
     */
    public static void reset() {
        allowDecrease = true;
    }
}
