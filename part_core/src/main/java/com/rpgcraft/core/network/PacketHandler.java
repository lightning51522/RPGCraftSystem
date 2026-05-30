package com.rpgcraft.core.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册管理器
 * 负责将自定义的网络包注册到 NeoForge 的网络系统中
 */
public class PacketHandler {

    /**
     * 监听网络注册事件，此方法需要在主类中添加到 Mod 事件总线
     * @param event 注册事件
     */
    public static void register(final RegisterPayloadHandlersEvent event) {
        // 创建注册器，参数 "1" 是网络协议版本号。
        // 如果客户端和服务端的协议版本不匹配，NeoForge 会拒绝连接以防止数据不同步
        final PayloadRegistrar registrar = event.registrar("1");

        // 注册从服务端发往客户端的包 (playToClient)
        // 参数 1：包的 TYPE 标识
        // 参数 2：包的序列化器 STREAM_CODEC
        // 参数 3：接收到包后的处理逻辑 handle 方法引用
        registrar.playToClient(
                SyncPlayerAttributePacket.TYPE,
                SyncPlayerAttributePacket.STREAM_CODEC,
                SyncPlayerAttributePacket::handle
        );
    }
}
