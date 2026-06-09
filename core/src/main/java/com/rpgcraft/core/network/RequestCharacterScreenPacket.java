package com.rpgcraft.core.network;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 角色界面请求包 —— 客户端到服务端
 * <p>
 * 客户端按下快捷键（默认 R）时发送此包，请求服务端创建全量属性快照并发送回客户端。
 * 服务端收到后通过 {@link SyncAttributeSnapshotPacket} 回传快照数据，
 * 客户端收到快照后缓存到 {@link com.rpgcraft.core.ui.UISnapshotCache} 供角色界面渲染使用。
 * <p>
 * 此包无字段，仅作为信号通知服务端发起快照同步。
 * <p>
 * <h3>数据流</h3>
 * <pre>
 * 客户端按下 R 键
 *   → CharacterScreenOpener 发送 RequestCharacterScreenPacket
 *   → 服务端 handle() 创建全量快照
 *   → 服务端通过 SyncAttributeSnapshotPacket 发送回客户端
 *   → 客户端缓存到 UISnapshotCache
 *   → RPGCharacterScreen 从缓存读取渲染
 * </pre>
 *
 * @see SyncAttributeSnapshotPacket
 * @see com.rpgcraft.core.ui.UISnapshotCache
 */
public record RequestCharacterScreenPacket() implements CustomPacketPayload {

    public static final Type<RequestCharacterScreenPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "request_character_screen")
    );

    /**
     * 无字段包使用 {@link StreamCodec#unit} 提供单例编解码
     * <p>
     * 无需读写任何数据，编解码时直接返回预构造的实例。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCharacterScreenPacket> STREAM_CODEC =
            StreamCodec.unit(new RequestCharacterScreenPacket());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：创建全量属性快照并发送回客户端
     * <p>
     * 通过 {@link IPayloadContext#enqueueWork} 将操作投递到服务端主线程执行，
     * 确保在正确的线程上下文中访问实体数据。
     * <p>
     * 快照创建委托给 {@link AttributeManager#getRegistry()} 的 {@code createSnapshot} 方法，
     * 遍历所有已注册属性捕获当前值和最大值。
     *
     * @param data    收到的请求数据（无字段）
     * @param context NeoForge 提供的网络上下文
     */
    public static void handle(RequestCharacterScreenPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            AttributeSnapshot snapshot = AttributeManager.getRegistry().createSnapshot(player);
            SyncAttributeSnapshotPacket.sendToClient(player, snapshot);
        });
    }
}
