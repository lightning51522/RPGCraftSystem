package com.rpgcraft.core.snapshot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全局死亡恢复模式的持久化存储。
 * <p>
 * 用于持久化 {@link DeathRestoreMode} 的当前模式（此前仅存在于 {@code volatile} 静态字段，
 * 服务端重启即重置为 {@link DeathRestoreMode#SNAPSHOT}）。改用 {@link SavedData} 挂载到
 * {@link MinecraftServer} 的数据存储后，{@code /rpg deathmode} 的切换可跨重启保留。
 * <p>
 * <h3>双层设计：SavedData 持久化 + 内存镜像</h3>
 * 与 {@code RandomSpawnSavedData} 一致：读路径走 {@link DeathRestoreMode#getCurrentMode()}
 * 的内存镜像（零开销），写路径 {@link #setCurrentMode(MinecraftServer, DeathRestoreMode)}
 * 同时更新内存与 SavedData（标记 dirty），服务端首次加载时从磁盘恢复内存镜像。
 * <p>
 * <h3>序列化策略</h3>
 * 用 {@link DeathRestoreMode#getCommandKey()}（"snapshot"/"rescan"）作为持久化字符串，
 * 反序列化时回查枚举；未知值（如旧存档或模组变更）回退到默认 {@link DeathRestoreMode#SNAPSHOT}。
 * 用 {@link Codec#STRING} 单字段，避免直接序列化枚举带来的脆弱性。
 */
public class DeathRestoreModeSavedData extends SavedData {

    /** NBT 字段名 */
    private static final String KEY_MODE = "death_restore_mode";

    /** 当前持久化的模式 key（commandKey 形式） */
    private String modeKey;

    /**
     * 序列化编解码器：存 commandKey 字符串，缺失时默认 "snapshot"。
     */
    public static final Codec<DeathRestoreModeSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf(KEY_MODE, DeathRestoreMode.SNAPSHOT.getCommandKey())
                            .forGetter(data -> data.modeKey)
            ).apply(instance, DeathRestoreModeSavedData::new)
    );

    /**
     * SavedData 类型标识：绑定 Identifier、默认构造器与 Codec。
     * 通过 {@code MinecraftServer#getDataStorage()} 访问（服务器全局，不绑定具体维度）。
     */
    public static final SavedDataType<DeathRestoreModeSavedData> TYPE = new SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("rpgcraftcore", "death_restore_mode"),
            DeathRestoreModeSavedData::new,
            CODEC
    );

    /** 默认构造函数（新建数据时使用，默认快照模式） */
    public DeathRestoreModeSavedData() {
        this.modeKey = DeathRestoreMode.SNAPSHOT.getCommandKey();
    }

    /** 反序列化构造函数 */
    public DeathRestoreModeSavedData(String modeKey) {
        this.modeKey = modeKey;
    }

    /**
     * 获取或创建服务器全局的持久化数据，并把磁盘值同步到 {@link DeathRestoreMode} 的内存镜像。
     *
     * @param server 当前服务端实例
     * @return 持久化数据（已从磁盘恢复）
     */
    public static DeathRestoreModeSavedData load(MinecraftServer server) {
        DeathRestoreModeSavedData data = server.getDataStorage().computeIfAbsent(TYPE);
        // 把磁盘值同步进 DeathRestoreMode 的内存镜像（未知 key 回退到 SNAPSHOT）
        DeathRestoreMode restored = DeathRestoreMode.fromCommandKey(data.modeKey);
        DeathRestoreMode.setCurrentMode(restored != null ? restored : DeathRestoreMode.SNAPSHOT);
        return data;
    }

    /**
     * 持久化新的死亡恢复模式（同时更新内存镜像与 SavedData）。
     *
     * @param server 当前服务端实例
     * @param mode   新模式
     */
    public static void setCurrentMode(MinecraftServer server, DeathRestoreMode mode) {
        DeathRestoreModeSavedData data = server.getDataStorage().computeIfAbsent(TYPE);
        data.modeKey = mode.getCommandKey();
        data.setDirty();
        // 同步内存镜像，保证读路径立即看到新值
        DeathRestoreMode.setCurrentMode(mode);
    }
}
