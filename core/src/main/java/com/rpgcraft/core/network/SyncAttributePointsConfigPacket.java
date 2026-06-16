package com.rpgcraft.core.network;

import com.rpgcraft.core.ui.AttributePointClientConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 属性点模块配置同步包 —— 服务端到客户端的单向同步
 * <p>
 * 将服务端 JSON 配置 {@code allow_decrease}（是否允许减少属性点）推送到客户端，
 * 供角色界面属性点面板决定是否渲染/响应 {@code [-]} 按钮。
 * <p>
 * 放在 core 模块（而非 attributepoints），使客户端处理器引用的
 * {@link AttributePointClientConfig} 也位于 core，避免 client 模块对
 * attributepoints 的编译期依赖。这与 {@link AllocateAttributePointPacket} 放在
 * core 的设计理由一致。
 *
 * @param allowDecrease 是否允许减少属性点
 */
public record SyncAttributePointsConfigPacket(boolean allowDecrease)
        implements CustomPacketPayload {

    public static final Type<SyncAttributePointsConfigPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_attribute_points_config")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAttributePointsConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SyncAttributePointsConfigPacket::allowDecrease,
                    SyncAttributePointsConfigPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将属性点配置推送给指定玩家
     *
     * @param player        目标玩家
     * @param allowDecrease 是否允许减少属性点
     */
    public static void sendToClient(ServerPlayer player, boolean allowDecrease) {
        player.connection.send(new SyncAttributePointsConfigPacket(allowDecrease));
    }

    /**
     * 客户端调用：处理从服务端收到的配置同步数据
     */
    public static void handle(SyncAttributePointsConfigPacket data, IPayloadContext context) {
        context.enqueueWork(() -> AttributePointClientConfig.set(data.allowDecrease()));
    }
}
