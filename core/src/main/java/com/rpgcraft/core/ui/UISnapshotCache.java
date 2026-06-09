package com.rpgcraft.core.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import org.jspecify.annotations.Nullable;

/**
 * UI 快照缓存（仅客户端运行时调用）
 * <p>
 * 缓存从服务端接收的全量属性快照，供角色界面（{@code RPGCharacterScreen}）读取渲染数据。
 * <p>
 * 数据流：
 * <pre>
 * 服务端发送 SyncAttributeSnapshotPacket
 *   → 客户端 handle() 调用 {@link #set(AttributeSnapshot)}
 *   → RPGCharacterScreen 打开时调用 {@link #get()} 读取
 *   → 关闭界面时调用 {@link #clear()} 清除
 * </pre>
 * <p>
 * 使用 {@code volatile} 保证网络线程写入和渲染线程读取之间的可见性。
 * 写入操作通过 {@code IPayloadContext.enqueueWork()} 已投递到客户端主线程，
 * {@code volatile} 是额外的安全保障。
 *
 * @see com.rpgcraft.core.network.SyncAttributeSnapshotPacket
 * @see AttributeSnapshot
 */
public final class UISnapshotCache {

    /** 缓存的属性快照（volatile 保证多线程可见性） */
    private static volatile AttributeSnapshot cached;

    private UISnapshotCache() {
        // 禁止实例化
    }

    /**
     * 设置缓存的快照
     * <p>
     * 由 {@link com.rpgcraft.core.network.SyncAttributeSnapshotPacket#handle} 在客户端主线程调用。
     *
     * @param snapshot 服务端同步的属性快照
     */
    public static void set(AttributeSnapshot snapshot) {
        cached = snapshot;
    }

    /**
     * 获取缓存的快照
     * <p>
     * 由角色界面在每帧渲染时调用。若返回 {@code null}，界面应显示"加载中"状态。
     *
     * @return 缓存的快照，若未收到服务端数据则返回 {@code null}
     */
    public static @Nullable AttributeSnapshot get() {
        return cached;
    }

    /**
     * 清除缓存的快照
     * <p>
     * 通常在角色界面关闭时调用，防止过期数据残留。
     */
    public static void clear() {
        cached = null;
    }
}
