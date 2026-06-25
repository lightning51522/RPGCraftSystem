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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
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
 * 携带完整职业状态（可分配经验池、当前主职业、已激活副职业集合、各职业等级、已解锁集合、
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

        // activeSecondary 集合
        buf.writeVarInt(v.activeSecondary().size());
        for (Identifier id : v.activeSecondary()) {
            Identifier.STREAM_CODEC.encode(buf, id);
        }

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
            // prerequisites 列表（空列表表示树根；单前置含 1 个；复合含多个）
            buf.writeVarInt(n.prerequisites().size());
            for (Identifier prereqId : n.prerequisites()) {
                Identifier.STREAM_CODEC.encode(buf, prereqId);
            }
            buf.writeBoolean(n.advanced());
            buf.writeByte(n.type().ordinal());
            buf.writeVarInt(n.maxLevel());
            // 图标：优先物品（带空标记），其次字符
            ItemStack item = n.iconItem();
            buf.writeBoolean(!item.isEmpty());
            if (!item.isEmpty()) {
                ItemStack.STREAM_CODEC.encode(buf, item);
            }
            buf.writeUtf(n.iconChar());
        }
    }

    private static SyncProfessionStatePacket decode(RegistryFriendlyByteBuf buf) {
        int pool = buf.readVarInt();
        Identifier currentMain = decodeNullableId(buf);

        int activeSize = buf.readVarInt();
        Set<Identifier> activeSecondary = new LinkedHashSet<>();
        for (int i = 0; i < activeSize; i++) {
            activeSecondary.add(Identifier.STREAM_CODEC.decode(buf));
        }

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
            int prereqSize = buf.readVarInt();
            List<Identifier> prereqs = new ArrayList<>(prereqSize);
            for (int j = 0; j < prereqSize; j++) {
                prereqs.add(Identifier.STREAM_CODEC.decode(buf));
            }
            boolean advanced = buf.readBoolean();
            IProfession.ProfessionType type = IProfession.ProfessionType.values()[buf.readByte()];
            int maxLevel = buf.readVarInt();
            ItemStack iconItem = ItemStack.EMPTY;
            if (buf.readBoolean()) {
                iconItem = ItemStack.STREAM_CODEC.decode(buf);
            }
            String iconChar = buf.readUtf();
            nodes.add(new ProfessionNode(id, name, desc, java.util.List.copyOf(prereqs), advanced, type, maxLevel, iconItem, iconChar));
        }
        return new SyncProfessionStatePacket(new ProfessionStateView(
                pool, currentMain, activeSecondary,
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
