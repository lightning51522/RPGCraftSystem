package com.rpgcraft.profession.network;

import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.profession.ProfessionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 职业同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 同步玩家的<b>完整</b>职业数据到客户端附件，供 HUD / 角色界面（{@code PlayerInfoPlugin}）读取。
 * <p>
 * 早期版本只同步 {@code professionId}，导致客户端附件缺少等级/已激活副职业/经验池 ——
 * 角色界面读取 {@code getProfessionLevel} 总返回 0（显示 Lv.1）、副职业行不显示。
 * 现改为同步完整状态：当前主职业 + 各职业等级 + 已激活副职业集合 + 经验池。
 *
 * @param professionId            当前主职业标识符
 * @param levels                  职业 ID → 等级
 * @param activeSecondary         已激活副职业集合（每个独立激活，加成共存）
 * @param skillPointPool          可分配职业经验池
 */
public record SyncPlayerProfessionPacket(
        @Nullable Identifier professionId,
        Map<Identifier, Integer> levels,
        Set<Identifier> activeSecondary,
        int skillPointPool
) implements CustomPacketPayload {

    public static final Type<SyncPlayerProfessionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_profession")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerProfessionPacket> STREAM_CODEC =
            StreamCodec.of(SyncPlayerProfessionPacket::encode, SyncPlayerProfessionPacket::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将完整职业数据发送给指定玩家
     *
     * @param player 服务端玩家
     * @param data   职业附件数据
     */
    public static void sendToClient(Player player, ProfessionData data) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new SyncPlayerProfessionPacket(
                    data.getProfessionId(),
                    new LinkedHashMap<>(data.getProfessionLevels()),
                    new LinkedHashSet<>(data.getActiveSecondaryProfessions()),
                    data.getSkillPointPool()
            ));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的职业同步数据
     * <p>
     * 直接写入客户端附件（不调用服务端权威方法，避免触发加成重算）。
     */
    public static void handle(SyncPlayerProfessionPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                ProfessionData profData = clientPlayer.getData(ProfessionManager.PLAYER_PROFESSION);
                if (data.professionId() != null) {
                    profData.setProfessionId(data.professionId());
                }
                // 同步等级表
                for (Map.Entry<Identifier, Integer> e : data.levels().entrySet()) {
                    profData.setProfessionLevel(e.getKey(), e.getValue());
                }
                // 同步已激活副职业集合（先清空再逐个设回）
                for (Identifier id : profData.getActiveSecondaryProfessions()) {
                    profData.setSecondaryActive(id, false);
                }
                for (Identifier id : data.activeSecondary()) {
                    profData.setSecondaryActive(id, true);
                }
                profData.setSkillPointPool(data.skillPointPool());
            }
        });
    }

    // ---- 序列化 ----

    private static void encode(RegistryFriendlyByteBuf buf, SyncPlayerProfessionPacket pkt) {
        encodeNullableId(buf, pkt.professionId());
        // levels
        buf.writeVarInt(pkt.levels().size());
        for (Map.Entry<Identifier, Integer> e : pkt.levels().entrySet()) {
            Identifier.STREAM_CODEC.encode(buf, e.getKey());
            buf.writeVarInt(e.getValue());
        }
        // activeSecondary
        buf.writeVarInt(pkt.activeSecondary().size());
        for (Identifier id : pkt.activeSecondary()) {
            Identifier.STREAM_CODEC.encode(buf, id);
        }
        buf.writeVarInt(pkt.skillPointPool());
    }

    private static SyncPlayerProfessionPacket decode(RegistryFriendlyByteBuf buf) {
        Identifier professionId = decodeNullableId(buf);
        int levelSize = buf.readVarInt();
        Map<Identifier, Integer> levels = new LinkedHashMap<>();
        for (int i = 0; i < levelSize; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            int lvl = buf.readVarInt();
            levels.put(id, lvl);
        }
        int activeSize = buf.readVarInt();
        Set<Identifier> activeSecondary = new LinkedHashSet<>();
        for (int i = 0; i < activeSize; i++) {
            activeSecondary.add(Identifier.STREAM_CODEC.decode(buf));
        }
        int skillPointPool = buf.readVarInt();
        return new SyncPlayerProfessionPacket(professionId, levels, activeSecondary, skillPointPool);
    }

    private static void encodeNullableId(RegistryFriendlyByteBuf buf, @Nullable Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) Identifier.STREAM_CODEC.encode(buf, id);
    }

    private static @Nullable Identifier decodeNullableId(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return Identifier.STREAM_CODEC.decode(buf);
    }
}
