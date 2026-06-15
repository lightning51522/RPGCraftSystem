package com.rpgcraft.attributepoints.network;

import com.rpgcraft.attributepoints.AttributePointsManager;
import com.rpgcraft.core.attributepoint.PlayerAttributePoints;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 属性点同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 同步玩家的可分配点数和各属性已分配点数到客户端，供角色界面的属性点面板显示。
 *
 * @param available   可分配点数
 * @param allocations 属性 ID → 已分配点数
 */
public record SyncPlayerAttributePointsPacket(int available, Map<Identifier, Integer> allocations)
        implements CustomPacketPayload {

    public static final Type<SyncPlayerAttributePointsPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_attribute_points")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerAttributePointsPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncPlayerAttributePointsPacket::available,
                    ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.INT),
                            SyncPlayerAttributePointsPacket::allocations,
                    SyncPlayerAttributePointsPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将属性点数据发送给指定玩家
     */
    public static void sendToClient(Player player, PlayerAttributePoints data) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new SyncPlayerAttributePointsPacket(
                    data.getAvailablePoints(),
                    new LinkedHashMap<>(data.getAllocations())
            ));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的属性点同步数据
     */
    public static void handle(SyncPlayerAttributePointsPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                PlayerAttributePoints apData = clientPlayer.getData(AttributePointsManager.PLAYER_ATTRIBUTE_POINTS);
                apData.setAvailablePoints(data.available());
                apData.clearAllocations();
                for (Map.Entry<Identifier, Integer> entry : data.allocations().entrySet()) {
                    apData.setAllocation(entry.getKey(), entry.getValue());
                }
            }
        });
    }
}
