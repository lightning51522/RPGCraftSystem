package com.rpgcraft.core.network;

import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * 职业动作请求包 —— 客户端到服务端
 * <p>
 * 玩家在职业面板执行操作（投入经验、进阶、切换主职业、切换某副职业的激活状态）
 * 时发送。服务端通过 {@link RPGSystems#getProfessionSystem()} 校验并应用，全部权威校验在
 * 服务端完成，防止客户端作弊。
 * <p>
 * 放置在 core 模块以避免 client 对 profession 的编译期依赖（参照
 * {@link AllocateAttributePointPacket}）。
 *
 * @param action       动作类型
 * @param professionId 目标职业 ID（TOGGLE_SECONDARY 指定要切换激活状态的副职业；其它动作必填）
 */
public record ProfessionActionPacket(Action action, @Nullable Identifier professionId) implements CustomPacketPayload {

    /**
     * 职业面板支持的客户端动作
     */
    public enum Action {
        /** 向目标职业投入一级 */
        INVEST,
        /** 进阶到目标职业 */
        ADVANCE,
        /** 切换当前主职业到目标职业 */
        SWITCH_MAIN,
        /** 切换指定副职业（{@code professionId}）的激活状态 */
        TOGGLE_SECONDARY,
        /** 解锁指定副职业（{@code professionId}）：消耗职业经验，基础副职业直接解锁，非基础需前置满级 */
        UNLOCK_SECONDARY
    }

    public static final Type<ProfessionActionPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "profession_action")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfessionActionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.idMapper(i -> Action.values()[i], Action::ordinal),
                    ProfessionActionPacket::action,
                    ByteBufCodecs.optional(Identifier.STREAM_CODEC),
                    p -> java.util.Optional.ofNullable(p.professionId()),
                    ProfessionActionPacket::new
            );

    /** 兼容 StreamCodec.optional 的构造器 */
    private ProfessionActionPacket(Action action, java.util.Optional<Identifier> professionId) {
        this(action, professionId.orElse(null));
    }

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ProfessionActionPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!RPGSystems.hasProfessionSystem()) return;
            var sys = RPGSystems.getProfessionSystem();
            Identifier id = data.professionId();
            switch (data.action()) {
                case INVEST -> { if (id != null) sys.investLevel(player, id); }
                case ADVANCE -> { if (id != null) sys.advance(player, id); }
                case SWITCH_MAIN -> { if (id != null) sys.switchMain(player, id); }
                case TOGGLE_SECONDARY -> {
                    if (id != null) sys.setSecondaryActive(player, id, !sys.isSecondaryActive(player, id));
                }
                case UNLOCK_SECONDARY -> { if (id != null) sys.unlockSecondary(player, id); }
            }
        });
    }
}
