package com.rpgcraft.core.network;

import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

/**
 * 属性点分配/回收请求包 —— 客户端到服务端
 * <p>
 * 玩家在角色界面的属性点面板点击 {@code [+]} 或 {@code [-]} 按钮时，客户端发送此包。
 * 服务端通过 {@link RPGSystems#getAttributePointSystem()} 委托给 attributepoints 模块校验
 * 后应用并同步，防止客户端作弊。
 * <p>
 * 放置在 core 模块的原因：client 模块的 UI 渲染器需要引用此包发送请求，放 core 可避免
 * client 对 attributepoints 模块的编译期依赖（保持 client 仅依赖 core）。
 *
 * @param attrId   目标属性 ID
 * @param points   点数（必须 &gt; 0）
 * @param allocate {@code true} 分配，{@code false} 回收
 */
public record AllocateAttributePointPacket(Identifier attrId, int points, boolean allocate) implements CustomPacketPayload {

    public static final Type<AllocateAttributePointPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "allocate_attribute_point")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, AllocateAttributePointPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC, AllocateAttributePointPacket::attrId,
                    ByteBufCodecs.VAR_INT, AllocateAttributePointPacket::points,
                    ByteBufCodecs.BOOL, AllocateAttributePointPacket::allocate,
                    AllocateAttributePointPacket::new
            );

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：处理分配/回收请求
     */
    public static void handle(AllocateAttributePointPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            // 委托属性点系统校验 + 应用 + 同步（服务端权威）
            if (RPGSystems.hasAttributePointSystem()) {
                if (data.allocate()) {
                    RPGSystems.getAttributePointSystem().allocate(player, data.attrId(), data.points());
                } else {
                    RPGSystems.getAttributePointSystem().deallocate(player, data.attrId(), data.points());
                }
            }
        });
    }
}

