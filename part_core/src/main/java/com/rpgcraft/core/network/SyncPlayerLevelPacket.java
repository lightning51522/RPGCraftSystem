package com.rpgcraft.core.network;

import com.rpgcraft.core.level.LevelManager;
import com.rpgcraft.core.level.PlayerLevelData;
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
 * 等级同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 同步玩家的等级、当前经验和升级所需经验到客户端，供 HUD 显示。
 *
 * @param level          当前等级
 * @param experience     当前累计经验
 * @param expForNextLevel 升到下一级所需经验，达到最大等级时为 -1
 */
public record SyncPlayerLevelPacket(int level, int experience, int expForNextLevel) implements CustomPacketPayload {

    public static final Type<SyncPlayerLevelPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_level")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerLevelPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerLevelPacket::level,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerLevelPacket::experience,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerLevelPacket::expForNextLevel,
            SyncPlayerLevelPacket::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将等级数据发送给指定玩家
     */
    public static void sendToClient(Player player, PlayerLevelData data) {
        if (player instanceof ServerPlayer serverPlayer) {
            int expForNext = data.getExpForNextLevel();
            serverPlayer.connection.send(new SyncPlayerLevelPacket(
                    data.getLevel(), data.getExperience(), expForNext
            ));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的等级同步数据
     */
    public static void handle(SyncPlayerLevelPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                PlayerLevelData levelData = clientPlayer.getData(LevelManager.PLAYER_LEVEL);
                levelData.setLevel(data.level());
                levelData.setExperience(data.experience());
            }
        });
    }
}
