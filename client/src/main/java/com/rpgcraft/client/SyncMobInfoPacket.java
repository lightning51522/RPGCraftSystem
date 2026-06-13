package com.rpgcraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 怪物信息同步包 —— 服务端到客户端
 * <p>
 * 服务端在收到 {@link QueryMobInfoPacket} 后，将怪物的评级、等级、生命值和 5 种属性
 * 通过此包回复给客户端，供 HUD 在左上角显示目标属性面板。
 * <p>
 * 包含的属性：
 * <ul>
 *   <li>生命值（currentHealth / maxHealth）</li>
 *   <li>力量（strength）</li>
 *   <li>法力（mana）</li>
 *   <li>防御（defense）</li>
 *   <li>法抗（resistance）</li>
 * </ul>
 *
 * @param entityId       实体 ID（与查询包对应）
 * @param ratingName     评级枚举名称（如 "NORMAL"、"ELITE"）
 * @param level          怪物等级
 * @param currentHealth  怪物当前生命值
 * @param maxHealth      怪物最大生命值
 * @param strength       力量
 * @param mana           法力
 * @param defense        防御
 * @param resistance     法抗
 * @param exp            击败该怪物可获得的经验值
 */
public record SyncMobInfoPacket(int entityId, String ratingName, int level,
                                int currentHealth, int maxHealth,
                                int strength, int mana, int defense, int resistance,
                                int exp) implements CustomPacketPayload {

    public static final Type<SyncMobInfoPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_mob_info")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncMobInfoPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::entityId,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, SyncMobInfoPacket::ratingName,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::level,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::currentHealth,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::maxHealth,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::strength,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::mana,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::defense,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::resistance,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncMobInfoPacket::exp,
            SyncMobInfoPacket::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：缓存怪物信息供 HUD 渲染使用
     */
    public static void handle(SyncMobInfoPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer == null) return;

            // 仅在实体 ID 仍然匹配当前准星指向的实体时才更新缓存
            // 防止过期的回复覆盖有效数据
            Entity target = Minecraft.getInstance().crosshairPickEntity;
            if (target != null && target.getId() == data.entityId()) {
                AttributeHudOverlay.cacheMobInfo(data.entityId(), data.ratingName(), data.level(),
                        data.currentHealth(), data.maxHealth(),
                        data.strength(), data.mana(), data.defense(), data.resistance(),
                        data.exp());
            }
        });
    }
}
