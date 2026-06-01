package com.rpgcraft.core.network;

import com.rpgcraft.core.client.AttributeHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * HUD 开关同步包 —— 服务端到客户端
 * <p>
 * 当玩家执行 {@code /rpg hud [on|off]} 指令时，服务端通过此包将开关状态同步给客户端。
 * 客户端收到后更新 {@link AttributeHudOverlay} 的 HUD 显示状态，
 * 同时控制左上角玩家属性面板和十字线准星提示。
 * 自定义生命条（{@code renderHealthBar}）不受此开关影响。
 *
 * @param enabled true = 启用 HUD，false = 禁用 HUD
 */
public record ToggleCrosshairPacket(boolean enabled) implements CustomPacketPayload {

    public static final Type<ToggleCrosshairPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "toggle_crosshair")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleCrosshairPacket> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.BOOL, ToggleCrosshairPacket::enabled,
            ToggleCrosshairPacket::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：更新 HUD 开关状态
     */
    public static void handle(ToggleCrosshairPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer == null) return;

            AttributeHudOverlay.setHudEnabled(data.enabled());
        });
    }
}
