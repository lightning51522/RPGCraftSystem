package com.rpgcraft.core.network;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.ui.ProfessionStateCache;
import com.rpgcraft.core.ui.ProfessionStateCache.ProfessionNode;
import com.rpgcraft.core.ui.ProfessionStateCache.ProfessionStateView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 职业状态同步包 —— 服务端到客户端
 * <p>
 * 携带完整职业状态（可分配经验池、当前主/副职业、副职业开关、各职业等级、已解锁集合、
 * 职业树节点元数据）。客户端收到后缓存到 {@link ProfessionStateCache} 供职业面板渲染。
 * <p>
 * StreamCodec 手写 encode/decode（参考 {@link SyncAttributeSnapshotPacket}）。
 */
public record SyncProfessionStatePacket(ProfessionStateView view) implements CustomPacketPayload {

    public static final Type<SyncProfessionStatePacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_profession_state")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProfessionStatePacket> STREAM_CODEC =
            StreamCodec.of(SyncProfessionStatePacket::encode, SyncProfessionStatePacket::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 服务端调用：发送状态到客户端 */
    public static void sendToClient(ServerPlayer player, ProfessionStateView view) {
        player.connection.send(new SyncProfessionStatePacket(view));
    }

    public static void handle(SyncProfessionStatePacket data, IPayloadContext context) {
        context.enqueueWork(() -> ProfessionStateCache.set(data.view()));
    }

    // ---- 序列化 ----

    private static void encode(RegistryFriendlyByteBuf buf, SyncProfessionStatePacket pkt) {
        ProfessionStateView v = pkt.view();
        buf.writeVarInt(v.pool());
        encodeNullableId(buf, v.currentMain());
        encodeNullableId(buf, v.currentSecondary());
        buf.writeBoolean(v.secondaryActive());

        // levels map
        buf.writeVarInt(v.levels().size());
        for (Map.Entry<Identifier, Integer> e : v.levels().entrySet()) {
            Identifier.STREAM_CODEC.encode(buf, e.getKey());
            buf.writeVarInt(e.getValue());
        }
        // unlocked set
        buf.writeVarInt(v.unlocked().size());
        for (Identifier id : v.unlocked()) {
            Identifier.STREAM_CODEC.encode(buf, id);
        }
        // nodes
        buf.writeVarInt(v.nodes().size());
        for (ProfessionNode n : v.nodes()) {
            Identifier.STREAM_CODEC.encode(buf, n.id());
            buf.writeUtf(n.displayName());
            buf.writeUtf(n.description());
            encodeNullableId(buf, n.prerequisite());
            buf.writeBoolean(n.advanced());
            buf.writeByte(n.type().ordinal());
        }
    }

    private static SyncProfessionStatePacket decode(RegistryFriendlyByteBuf buf) {
        int pool = buf.readVarInt();
        Identifier currentMain = decodeNullableId(buf);
        Identifier currentSecondary = decodeNullableId(buf);
        boolean secondaryActive = buf.readBoolean();

        int levelSize = buf.readVarInt();
        Map<Identifier, Integer> levels = new LinkedHashMap<>();
        for (int i = 0; i < levelSize; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            int lvl = buf.readVarInt();
            levels.put(id, lvl);
        }
        int unlockedSize = buf.readVarInt();
        Set<Identifier> unlocked = new LinkedHashSet<>();
        for (int i = 0; i < unlockedSize; i++) {
            unlocked.add(Identifier.STREAM_CODEC.decode(buf));
        }
        int nodeSize = buf.readVarInt();
        List<ProfessionNode> nodes = new ArrayList<>();
        for (int i = 0; i < nodeSize; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            String name = buf.readUtf();
            String desc = buf.readUtf();
            Identifier prereq = decodeNullableId(buf);
            boolean advanced = buf.readBoolean();
            IProfession.ProfessionType type = IProfession.ProfessionType.values()[buf.readByte()];
            nodes.add(new ProfessionNode(id, name, desc, prereq, advanced, type));
        }
        return new SyncProfessionStatePacket(new ProfessionStateView(
                pool, currentMain, currentSecondary, secondaryActive,
                levels, unlocked, nodes));
    }

    private static void encodeNullableId(RegistryFriendlyByteBuf buf, @Nullable Identifier id) {
        buf.writeBoolean(id != null);
        if (id != null) Identifier.STREAM_CODEC.encode(buf, id);
    }

    private static @Nullable Identifier decodeNullableId(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return Identifier.STREAM_CODEC.decode(buf);
    }
}
