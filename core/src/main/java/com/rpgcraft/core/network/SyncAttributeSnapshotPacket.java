package com.rpgcraft.core.network;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.AttributeSnapshot.AttributeData;
import com.rpgcraft.core.ui.UISnapshotCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全量属性快照同步网络包 —— 服务端到客户端
 * <p>
 * 打开角色界面时，服务端一次性将所有属性数据打包发送给客户端。
 * 客户端收到后缓存到 {@link UISnapshotCache}，供 {@code RPGCharacterScreen} 读取渲染。
 * <p>
 * 与 {@link SyncPlayerAttributePacket}（单属性实时同步）不同，此包用于
 * UI 场景的批量快照同步，避免逐属性发送的网络开销。
 * <p>
 * <h3>数据流</h3>
 * <pre>
 * 玩家请求打开角色界面（快捷键/命令）
 *   → 服务端调用 {@link #sendToClient} 发送全量快照
 *   → 客户端 {@link #handle} 将快照写入 {@link UISnapshotCache}
 *   → RPGCharacterScreen 从 {@link UISnapshotCache#get()} 读取渲染数据
 * </pre>
 * <p>
 * <h3>编码格式</h3>
 * <pre>
 * VarInt(size) + [
 *   Identifier(key) + VarInt(currentValue) + VarInt(maxValue) + UTF(displayName)
 * ] × N
 * </pre>
 *
 * @see UISnapshotCache
 * @see AttributeSnapshot
 */
public record SyncAttributeSnapshotPacket(AttributeSnapshot snapshot) implements CustomPacketPayload {

    public static final Type<SyncAttributeSnapshotPacket> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "sync_snapshot")
    );

    /**
     * 网络字节流序列化器
     * <p>
     * 手动编解码 {@link AttributeSnapshot} 内部的 {@code Map<Identifier, AttributeData>}。
     * 使用 VarInt 编码属性值（通常为小整数，比固定 4 字节 int 更节省带宽），
     * UTF 编码属性显示名称。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncAttributeSnapshotPacket> STREAM_CODEC =
            StreamCodec.of(SyncAttributeSnapshotPacket::encode, SyncAttributeSnapshotPacket::decode);

    @Override
    public @NonNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端调用：将全量属性快照发送给指定玩家
     * <p>
     * 快照通常通过 {@link com.rpgcraft.core.attribute.api.IAttributeRegistry#createSnapshot} 创建。
     *
     * @param player   目标玩家（必须为 ServerPlayer）
     * @param snapshot 属性快照
     */
    public static void sendToClient(ServerPlayer player, AttributeSnapshot snapshot) {
        player.connection.send(new SyncAttributeSnapshotPacket(snapshot));
    }

    /**
     * 客户端调用：处理从服务端收到的全量快照
     * <p>
     * 通过 {@link IPayloadContext#enqueueWork} 将操作投递到客户端主线程执行，
     * 避免在网络线程上直接修改缓存导致并发问题。
     *
     * @param data    收到的快照数据
     * @param context NeoForge 提供的网络上下文
     */
    public static void handle(SyncAttributeSnapshotPacket data, IPayloadContext context) {
        context.enqueueWork(() -> {
            UISnapshotCache.set(data.snapshot());
        });
    }

    // ---- 序列化 ----

    private static void encode(RegistryFriendlyByteBuf buf, SyncAttributeSnapshotPacket pkt) {
        Map<Identifier, AttributeData> map = pkt.snapshot().getAll();
        buf.writeVarInt(map.size());
        for (Map.Entry<Identifier, AttributeData> entry : map.entrySet()) {
            Identifier.STREAM_CODEC.encode(buf, entry.getKey());
            AttributeData ad = entry.getValue();
            buf.writeVarInt(ad.currentValue());
            buf.writeVarInt(ad.maxValue());
            buf.writeUtf(ad.displayName());
        }
    }

    private static SyncAttributeSnapshotPacket decode(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Identifier, AttributeData> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            Identifier id = Identifier.STREAM_CODEC.decode(buf);
            int current = buf.readVarInt();
            int max = buf.readVarInt();
            String displayName = buf.readUtf();
            map.put(id, new AttributeData(current, max, displayName));
        }
        return new SyncAttributeSnapshotPacket(new AttributeSnapshot(map));
    }
}
