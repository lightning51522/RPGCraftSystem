package com.rpgcraft.core.network;

import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 技能释放请求包 —— 客户端到服务端
 * <p>
 * 玩家按下技能快捷键时，客户端发送此包。服务端通过 {@link RPGSystems#getSkillSystem()}
 * 委托给 skills 模块校验（冷却/资源/已学）后应用并同步，防止客户端作弊。
 * <p>
 * 放置在 core 模块的原因：client 模块（或 skills 客户端代码）要发送此包，放 core 可避免
 * 发送方对 skills 模块的编译期依赖（参照 {@link AllocateAttributePointPacket}）。
 *
 * @param skillId 技能 ID
 */
public record CastSkillPacket(Identifier skillId) implements CustomPacketPayload {

    public static final Type<CastSkillPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "cast_skill")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CastSkillPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, CastSkillPacket::skillId,
                    CastSkillPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：处理技能释放请求
     */
    public static void handle(CastSkillPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // 委托技能系统校验 + 应用 + 同步（服务端权威）
            if (RPGSystems.hasSkillSystem()) {
                RPGSystems.getSkillSystem().cast(player, data.skillId());
            }
        });
    }
}
