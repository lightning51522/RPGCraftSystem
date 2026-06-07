package com.rpgcraft.core.network;

import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.ProfessionManager;
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
 * 职业同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 同步玩家的当前职业标识符到客户端，供 HUD 显示。
 *
 * @param professionId 当前主职业标识符
 */
public record SyncPlayerProfessionPacket(Identifier professionId) implements CustomPacketPayload {

    public static final Type<SyncPlayerProfessionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_profession")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerProfessionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, SyncPlayerProfessionPacket::professionId,
                    SyncPlayerProfessionPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将职业数据发送给指定玩家
     *
     * @param player 服务端玩家
     * @param data   职业附件数据
     */
    public static void sendToClient(Player player, ProfessionData data) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new SyncPlayerProfessionPacket(data.getProfessionId()));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的职业同步数据
     */
    public static void handle(SyncPlayerProfessionPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                ProfessionData profData = clientPlayer.getData(ProfessionManager.PLAYER_PROFESSION);
                profData.setProfessionId(data.professionId());
            }
        });
    }
}
