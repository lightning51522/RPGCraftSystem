package com.rpgcraft.core.network;

import com.rpgcraft.core.attribute.GenericPlayerData;
import com.rpgcraft.core.attribute.PlayerAttribute;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 属性同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 当服务端上的玩家属性发生变化（或玩家首次登录）时，服务端构造此数据包并发送给客户端。
 * 客户端收到后，将属性值写入本地玩家的 Attachment 中，供 HUD 渲染读取。
 * <p>
 * <b>数据流：</b>
 * <pre>
 * 服务端: player.getData(attachmentType) → SyncPlayerAttributePacket → 网络传输
 * 客户端: SyncPlayerAttributePacket.handle() → clientPlayer.getData(type).setValue()
 * </pre>
 * <p>
 * <b>序列化说明：</b>
 * <ul>
 *   <li>{@link #STREAM_CODEC} —— 用于网络传输的字节流序列化器（StreamCodec）</li>
 *   <li>{@link PlayerAttribute#CODEC} —— 用于存档保存的 NBT/JSON 序列化器（MapCodec）</li>
 *   <li>两者用途不同，不可混用</li>
 * </ul>
 *
 * @param attrId  属性的 Identifier，用于在客户端查找对应的 AttachmentType
 * @param current 属性当前值
 * @param max     属性最大值
 */
public record SyncPlayerAttributePacket(Identifier attrId, int current, int max) implements CustomPacketPayload {

    /**
     * 此网络包的通道标识符（Payload Type）
     * <p>
     * NeoForge 通过此标识符区分不同的自定义网络包。
     * 在 {@link PacketHandler#register} 中注册时与此 TYPE 绑定。
     */
    public static final Type<SyncPlayerAttributePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_attr")
    );

    /**
     * 网络流序列化器（StreamCodec）
     * <p>
     * 负责将数据包写入字节流（发送端）和从字节流读取（接收端）。
     * 使用 {@link StreamCodec#composite} 将三个字段按顺序组合。
     * <p>
     * 序列化顺序必须与 composite 参数声明顺序一致：
     * <ol>
     *   <li>{@link Identifier} —— 属性 ID</li>
     *   <li>{@code int} —— 当前值</li>
     *   <li>{@code int} —— 最大值</li>
     * </ol>
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerAttributePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SyncPlayerAttributePacket::attrId,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::current,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::max,
            SyncPlayerAttributePacket::new
    );

    /**
     * 返回此包的 Payload Type，NeoForge 内部使用此方法路由网络包
     *
     * @return 包的类型标识
     */
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将单个属性数据发送给指定玩家
     * <p>
     * 仅在服务端有效。通过 {@code instanceof ServerPlayer} 检查确保不会在客户端调用。
     * 发送路径：服务端 → {@code serverPlayer.connection.send()} → 网络层 → 客户端
     *
     * @param player 目标玩家（必须是服务端玩家实例）
     * @param attrId 属性的 Identifier，客户端通过此 ID 查找对应的 AttachmentType
     * @param attr   要同步的属性数据对象
     */
    public static void sendToClient(Player player, Identifier attrId, PlayerAttribute attr) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new SyncPlayerAttributePacket(attrId, attr.getValue(), attr.getMaxValue()));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的属性同步数据
     * <p>
     * 将收到的属性值覆盖写入客户端本地玩家的 Attachment 中。
     * 通过 {@link IPayloadContext#enqueueWork} 确保数据写入操作在主线程执行，
     * 避免网络线程直接修改游戏数据导致的并发问题。
     *
     * @param data    收到的网络包内容
     * @param context 网络上下文，提供线程安全的任务调度
     */
    public static void handle(SyncPlayerAttributePacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                // 通过包中的 Identifier 查找对应的 AttachmentType
                AttachmentType<PlayerAttribute> type = GenericPlayerData.getTypeById(data.attrId());
                if (type != null) {
                    // 从客户端玩家身上获取属性实例，并用服务端发来的数据覆盖
                    PlayerAttribute clientAttr = clientPlayer.getData(type);
                    clientAttr.setValue(data.current());
                }
            }
        });
    }
}
