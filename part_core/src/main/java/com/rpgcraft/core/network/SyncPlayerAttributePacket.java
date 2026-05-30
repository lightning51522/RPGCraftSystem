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
 * 属性同步网络包
 * 26.1 版本使用 record 定义包结构，极度简化了代码
 */
public record SyncPlayerAttributePacket(Identifier attrId, int current, int max) implements CustomPacketPayload {

    // 定义此网络包的唯一标识符 (通道名)
    public static final Type<SyncPlayerAttributePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_attr")
    );

    /**
     * StreamCodec 负责将数据包写入字节流(发送)和从字节流读取(接收)
     * 注意：网络传输使用 StreamCodec，存档保存使用 MapCodec，两者不要混淆！
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerAttributePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SyncPlayerAttributePacket::attrId,   // 属性的 ID
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::current, // 当前值
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::max,      // 最大值
            SyncPlayerAttributePacket::new // 解码后调用的构造方法
    );

    // 必须实现的接口方法，返回包的类型
    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用此方法将数据发送给指定的玩家
     * @param player 目标玩家
     * @param attrId 属性的 Identifier
     * @param attr   属性数据对象
     */
    public static void sendToClient(Player player, Identifier attrId, PlayerAttribute attr) {
        // 确保只在服务端执行发送逻辑，并且玩家是 ServerPlayer 实例
        if (player instanceof ServerPlayer serverPlayer) {
            // 26.1 中直接使用原版网络通道发送，不再需要 SimpleChannel
            serverPlayer.connection.send(new SyncPlayerAttributePacket(attrId, attr.getValue(), attr.getMaxValue()));
        }
    }

    /**
     * 客户端收到数据包后的处理逻辑
     * @param data 收到的数据包内容
     * @param context 网络上下文
     */
    public static void handle(SyncPlayerAttributePacket data, IPayloadContext context) {
        // enqueueWork 确保将修改操作放入主线程执行，避免多线程并发修改导致崩溃
        context.enqueueWork(() -> {
            // 获取客户端的本地玩家实例
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                // 通过包里的 ID，找到对应的 AttachmentType
                AttachmentType<PlayerAttribute> type = GenericPlayerData.getTypeById(data.attrId());
                if (type != null) {
                    // 从客户端玩家身上获取属性实例，并用服务端发来的数据覆盖它
                    PlayerAttribute clientAttr = clientPlayer.getData(type);
                    clientAttr.setValue(data.current());
                    // 如果你的最大值也会动态改变，记得在 PlayerAttribute 中添加更新最大值的方法并在此调用
                }
            }
        });
    }
}
