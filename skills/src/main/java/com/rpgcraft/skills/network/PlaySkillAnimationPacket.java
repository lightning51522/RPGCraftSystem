package com.rpgcraft.skills.network;

import com.rpgcraft.skills.client.SkillAnimationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 技能动画播放网络包 —— 服务端到客户端
 * <p>
 * 服务端释放技能时发送，客户端收到后通过 PAL 播放对应的玩家动画。
 * <p>
 * 放在 skills 模块（而非 core）的原因：仅 skills 内部触发，handler 直接调 PAL API，
 * 符合"模块自有同步包"模式，避免 core 依赖 skills 客户端代码。
 *
 * @param animationId PAL 动画资源 ID（对应 {@code assets/<ns>/player_animations/<path>.json}）
 */
public record PlaySkillAnimationPacket(Identifier animationId)
        implements CustomPacketPayload {

    public static final Type<PlaySkillAnimationPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "play_skill_animation")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaySkillAnimationPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, PlaySkillAnimationPacket::animationId,
                    PlaySkillAnimationPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端调用：收到服务端指令后播放技能动画
     */
    public static void handle(PlaySkillAnimationPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            var clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                SkillAnimationHandler.playSkillAnimation(clientPlayer, data.animationId());
            } else {
                com.mojang.logging.LogUtils.getLogger().warn("[技能动画] 收到动画包但 clientPlayer 为 null，id={}", data.animationId());
            }
        });
    }
}
