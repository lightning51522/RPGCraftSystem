package com.rpgcraft.core.network;

import com.rpgcraft.core.ui.ProfessionClientConfig;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 职业模块配置同步包 —— 服务端到客户端的单向同步
 * <p>
 * 将服务端 JSON 配置（{@code secondary_unlock_cost}、{@code default_max_level}、
 * {@code allow_downgrade_switch}）推送到客户端，供职业面板显示解锁消耗、判断投入/解锁可用性。
 * <p>
 * 放在 core 模块（而非 profession），使客户端处理器引用的 {@link ProfessionClientConfig}
 * 也位于 core，避免 client 模块对 profession 的编译期依赖。这与
 * {@link SyncAttributePointsConfigPacket} 放在 core 的设计理由一致。
 *
 * @param secondaryUnlockCost 副职业解锁消耗的职业经验
 * @param defaultMaxLevel     职业默认等级上限
 * @param allowDowngradeSwitch 是否允许从进阶职业降级切回基础
 */
public record SyncProfessionConfigPacket(int secondaryUnlockCost, int defaultMaxLevel,
                                         boolean allowDowngradeSwitch)
        implements CustomPacketPayload {

    public static final Type<SyncProfessionConfigPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_profession_config")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProfessionConfigPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SyncProfessionConfigPacket::secondaryUnlockCost,
                    ByteBufCodecs.INT, SyncProfessionConfigPacket::defaultMaxLevel,
                    ByteBufCodecs.BOOL, SyncProfessionConfigPacket::allowDowngradeSwitch,
                    SyncProfessionConfigPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将职业配置推送给指定玩家
     */
    public static void sendToClient(ServerPlayer player, int secondaryUnlockCost,
                                    int defaultMaxLevel, boolean allowDowngradeSwitch) {
        player.connection.send(new SyncProfessionConfigPacket(
                secondaryUnlockCost, defaultMaxLevel, allowDowngradeSwitch));
    }

    /**
     * 客户端调用：处理从服务端收到的配置同步数据
     */
    public static void handle(SyncProfessionConfigPacket data, IPayloadContext context) {
        context.enqueueWork(() -> ProfessionClientConfig.set(
                data.secondaryUnlockCost(), data.defaultMaxLevel(), data.allowDowngradeSwitch()));
    }
}
