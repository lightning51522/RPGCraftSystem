package com.rpgcraft.client.network;

import com.rpgcraft.client.ui.RPGCharacterScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 打开角色界面信号包 —— 服务端到客户端
 * <p>
 * 当玩家执行 {@code /rpg character} 命令时，服务端在发送完属性快照后，
 * 再发送此包通知客户端打开角色界面。
 * <p>
 * 此包无字段，仅作为信号通知。配合 {@link com.rpgcraft.core.network.SyncAttributeSnapshotPacket}
 * 使用，确保快照数据先到达客户端并缓存，再打开界面。
 * <p>
 * <h3>数据流</h3>
 * <pre>
 * 玩家执行 /rpg character
 *   → 服务端创建全量快照，发送 SyncAttributeSnapshotPacket
 *   → 服务端发送 OpenCharacterScreenPacket
 *   → 客户端收到快照 → 缓存到 UISnapshotCache
 *   → 客户端收到信号 → 打开 RPGCharacterScreen
 * </pre>
 * <p>
 * TCP 保证两个包的到达顺序，且 {@code enqueueWork} 保证处理顺序，
 * 因此快照一定在界面打开前就已缓存完毕。
 *
 * @see com.rpgcraft.client.CharacterCommands
 * @see RPGCharacterScreen
 */
public record OpenCharacterScreenPacket() implements CustomPacketPayload {

    public static final Type<OpenCharacterScreenPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "open_character_screen")
    );

    /**
     * 无字段包使用 {@link StreamCodec#unit} 提供单例编解码
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCharacterScreenPacket> STREAM_CODEC =
            StreamCodec.unit(new OpenCharacterScreenPacket());

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：打开角色界面
     * <p>
     * 通过 {@link IPayloadContext#enqueueWork} 投递到客户端主线程执行，
     * 确保在正确的线程上下文中操作 Minecraft 实例。
     *
     * @param data    收到的信号数据（无字段）
     * @param context NeoForge 提供的网络上下文
     */
    public static void handle(OpenCharacterScreenPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.setScreen(new RPGCharacterScreen());
            }
        });
    }
}
