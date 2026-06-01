package com.rpgcraft.core.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册管理器
 * <p>
 * 负责将模组所有自定义网络包注册到 NeoForge 的网络系统中。
 * 在主类构造函数中通过 {@code modEventBus.addListener(PacketHandler::register)} 挂载到 Mod 事件总线。
 * <p>
 * 注册时机为模组加载阶段（FML lifecycle），早于游戏启动，确保网络包在玩家连接前就已准备就绪。
 */
public class PacketHandler {

    /**
     * 网络包注册回调
     * <p>
     * 在 Mod 事件总线上监听 {@link RegisterPayloadHandlersEvent} 事件（自动检测为 Mod 总线事件）。
     * 通过 {@link PayloadRegistrar} 注册每个网络包的方向和处理器。
     *
     * @param event NeoForge 提供的网络包注册事件
     */
    public static void register(final RegisterPayloadHandlersEvent event) {
        // 创建注册器，参数 "1" 是网络协议版本号。
        // 如果客户端和服务端的协议版本不匹配，NeoForge 会拒绝连接以防止数据不同步
        final PayloadRegistrar registrar = event.registrar("1");

        // 注册从服务端发往客户端的属性同步包（playToClient）
        // 参数说明：
        //   TYPE        —— 包的类型标识，用于路由
        //   STREAM_CODEC—— 包的序列化/反序列化器
        //   handle      —— 客户端收到包后的处理方法引用
        registrar.playToClient(
                SyncPlayerAttributePacket.TYPE,
                SyncPlayerAttributePacket.STREAM_CODEC,
                SyncPlayerAttributePacket::handle
        );

        // 注册等级同步包
        registrar.playToClient(
                SyncPlayerLevelPacket.TYPE,
                SyncPlayerLevelPacket.STREAM_CODEC,
                SyncPlayerLevelPacket::handle
        );
    }
}
