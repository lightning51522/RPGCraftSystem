package com.rpgcraft.core.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 职业面板状态请求包 —— 客户端到服务端
 * <p>
 * 客户端按下职业面板快捷键（默认 P）时发送，请求服务端组装完整职业状态
 * （含职业树元数据与玩家进度）并通过 {@link SyncProfessionStatePacket} 回传。
 * 客户端收到后缓存到 {@link com.rpgcraft.core.ui.ProfessionStateCache} 供面板渲染。
 * <p>
 * 无字段，仅作信号。参照 {@link RequestCharacterScreenPacket}。
 *
 * @see SyncProfessionStatePacket
 * @see com.rpgcraft.core.ui.ProfessionStateCache
 */
public record RequestProfessionStatePacket() implements CustomPacketPayload {

    public static final Type<RequestProfessionStatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "request_profession_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestProfessionStatePacket> STREAM_CODEC =
            StreamCodec.unit(new RequestProfessionStatePacket());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理：组装职业状态并发送回客户端
     * <p>
     * 实际组装逻辑通过 {@code ProfessionStateAssembler}（由 profession 模块注册到 RPGSystems 的
     * 回调）完成，避免 core 对 profession 的编译期依赖。
     */
    public static void handle(RequestProfessionStatePacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // 委托给 profession 模块注册的状态组装器
            ProfessionStateAssembler.Assembler assembler = ProfessionStateAssembler.get();
            if (assembler != null) {
                SyncProfessionStatePacket.sendToClient(player, assembler.build(player));
            }
        });
    }
}
