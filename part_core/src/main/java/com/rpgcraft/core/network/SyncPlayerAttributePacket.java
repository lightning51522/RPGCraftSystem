package com.rpgcraft.core.network;

import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.attribute.api.IAttribute;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 属性同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 当服务端上的玩家属性发生变化（或玩家首次登录）时，服务端构造此数据包并发送给客户端。
 * 客户端收到后，将属性值写入本地玩家的 Attachment 中，供 HUD 渲染读取。
 *
 * @param attrId  属性的 Identifier，用于在客户端查找对应的 AttachmentType
 * @param current 属性当前值
 * @param max     属性最大值
 */
public record SyncPlayerAttributePacket(Identifier attrId, int current, int max) implements CustomPacketPayload {

    public static final Type<SyncPlayerAttributePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_attr")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerAttributePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SyncPlayerAttributePacket::attrId,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::current,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::max,
            SyncPlayerAttributePacket::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将单个属性数据发送给指定玩家
     */
    public static void sendToClient(Player player, Identifier attrId, EntityAttribute attr) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new SyncPlayerAttributePacket(attrId, attr.getValue(), attr.getMaxValue()));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的属性同步数据
     * <p>
     * 通过注册中心查找 AttachmentType，写入客户端玩家的属性数据。
     */
    public static void handle(SyncPlayerAttributePacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                var type = GenericEntityData.getRegistry().getTypeById(data.attrId());
                if (type != null) {
                    IAttribute attr = clientPlayer.getData(type);
                    attr.setValue(data.current());
                }
            }
        });
    }
}
