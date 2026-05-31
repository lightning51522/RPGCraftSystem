package com.rpgcraft.core.network;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 属性同步网络包 —— 服务端到客户端的单向同步
 * <p>
 * 当服务端上的玩家属性发生变化（或玩家首次登录）时，服务端构造此数据包并发送给客户端。
 * 客户端收到后，将属性值写入本地玩家的 Attachment 中，供 HUD 渲染读取。
 * <p>
 * <h3>完整网络同步流程</h3>
 * <pre>
 * 服务端属性变更（命令设置/战斗伤害/装备穿脱等）
 *   → {@link #sendToClient} 构造包并通过 ServerPlayer.connection 发送
 *   → NeoForge 网络层通过 {@link #STREAM_CODEC} 序列化
 *   → 客户端收到后通过 {@link #handle} 处理
 *     → {@code context.enqueueWork()} 将操作投递到客户端主线程（确保线程安全）
 *       → 通过 {@link AttributeManager#getRegistry()} 查找 AttachmentType
 *       → 先 {@code setMaxValue()} 再 {@code setValue()} 更新客户端附件
 *   → 下一帧 HUD 渲染时从附件读取最新值并显示
 * </pre>
 * <p>
 * <h3>为什么必须同时设置 max 和 current？</h3>
 * 客户端附件独立于服务端，不会自动同步。如果只设置 currentValue 而不设置 maxValue，
 * 客户端的 maxValue 可能与服务端不一致（例如装备改变上限后未同步），
 * 导致 HUD 显示的 "当前值/最大值" 格式错误。
 * <p>
 * <h3>StreamCodec vs MapCodec</h3>
 * 此包使用 {@link StreamCodec} 进行网络字节流序列化，与 {@link EntityAttribute#CODEC}
 * 使用的 {@link com.mojang.serialization.MapCodec}（存档 NBT 序列化）是两套独立的序列化系统，
 * 不可混用。
 *
 * @param attrId  属性的 Identifier，用于在客户端查找对应的 AttachmentType
 * @param current 属性当前值
 * @param max     属性最大值
 */
public record SyncPlayerAttributePacket(Identifier attrId, int current, int max) implements CustomPacketPayload {

    public static final Type<SyncPlayerAttributePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_attr")
    );

    /** 网络字节流序列化器：按 Identifier → int → int 的固定顺序读写 */
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPlayerAttributePacket> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, SyncPlayerAttributePacket::attrId,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::current,
            net.minecraft.network.codec.ByteBufCodecs.INT, SyncPlayerAttributePacket::max,
            SyncPlayerAttributePacket::new
    );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将单个属性数据发送给指定玩家
     * <p>
     * 内部会检查 player 是否为 {@link ServerPlayer}（非 ServerPlayer 时静默跳过），
     * 确保不会向非服务端玩家发送网络包。
     *
     * @param player 目标玩家（通常为 ServerPlayer）
     * @param attrId 属性标识符
     * @param attr   属性实例（读取 value 和 maxValue）
     */
    public static void sendToClient(Player player, Identifier attrId, EntityAttribute attr) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new SyncPlayerAttributePacket(attrId, attr.getValue(), attr.getMaxValue()));
        }
    }

    /**
     * 客户端调用：处理从服务端收到的属性同步数据
     * <p>
     * 通过 {@link IPayloadContext#enqueueWork} 将操作投递到客户端主线程执行，
     * 避免在网络线程上直接修改游戏状态导致并发问题。
     * <p>
     * 操作顺序：先 {@code setMaxValue} 再 {@code setValue}。
     * 必须先设置上限，否则 setValue 的 clamp 可能用旧的 maxValue 截断新值。
     *
     * @param data    收到的同步数据
     * @param context NeoForge 提供的网络上下文
     */
    public static void handle(SyncPlayerAttributePacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player clientPlayer = Minecraft.getInstance().player;
            if (clientPlayer != null) {
                var type = AttributeManager.getRegistry().getTypeById(data.attrId());
                if (type != null) {
                    IAttribute attr = clientPlayer.getData(type);
                    attr.setMaxValue(data.max());
                    attr.setValue(data.current());
                }
            }
        });
    }
}
