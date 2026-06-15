package com.rpgcraft.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * 全局随机刷新开关的持久化存储
 * <p>
 * 用于持久化 {@link CombatCommands} 的"随机刷新"全局开关状态。
 * 此前该状态是一个 {@code volatile} 静态字段，服务端重启即重置为 {@code false}；
 * 改用 {@link SavedData} 挂载到 {@link MinecraftServer} 的数据存储后，状态可跨重启保留。
 * <p>
 * <h3>双层设计：SavedData 持久化 + 内存缓存</h3>
 * 自然生成的怪物每次入世界都会查询此开关（{@link CombatEventHandler#onEntityJoinLevel}），
 * 频次高。为避免每次都走 {@code DataStorage} 查询，本类同时维护一个 {@code volatile}
 * 内存镜像：
 * <ul>
 *   <li>读路径 {@link #isEnabled()} 读内存镜像 —— 零开销</li>
 *   <li>写路径 {@link #setEnabled(MinecraftServer, boolean)} 同时更新内存镜像和 SavedData（标记 dirty）</li>
 *   <li>服务端首次加载时从磁盘恢复内存镜像</li>
 * </ul>
 * 内存镜像在服务端启动后即常驻，{@code DataStorage} 只负责持久化到存档文件。
 * <p>
 * <h3>MC 26.1 SavedData API</h3>
 * 采用 MC 26.1 的 {@link SavedDataType} + {@link Codec} 模式（而非旧的
 * {@code save/load(CompoundTag)} 重写），由 Codec 自动处理布尔值的 NBT 序列化，
 * 避开 26.1 中 {@code CompoundTag.getBoolean} 返回 {@code Optional<Boolean>} 的 API 变更。
 */
public class RandomSpawnSavedData extends SavedData {

    /** NBT 字段名 */
    private static final String KEY_ENABLED = "random_spawn_enabled";

    /** 内存镜像：供高频读路径直接访问，避免每次查询 DataStorage */
    private static volatile boolean cachedEnabled = false;

    /** 当前持久化状态 */
    private boolean randomSpawnEnabled;

    /**
     * 序列化编解码器（Codec 模式，由 SavedDataType 使用）
     */
    public static final Codec<RandomSpawnSavedData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf(KEY_ENABLED, false)
                            .forGetter(data -> data.randomSpawnEnabled)
            ).apply(instance, RandomSpawnSavedData::new)
    );

    /**
     * SavedData 类型标识：绑定 Identifier、默认构造器与 Codec。
     * <p>
     * 通过 {@code MinecraftServer#getDataStorage()} 访问（服务器全局，不绑定具体维度）。
     */
    public static final SavedDataType<RandomSpawnSavedData> TYPE = new SavedDataType<>(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("rpgcraftattributes", "random_spawn"),
            RandomSpawnSavedData::new,
            CODEC
    );

    /**
     * 默认构造函数（新建数据时使用，开关默认关闭）
     */
    public RandomSpawnSavedData() {
        this.randomSpawnEnabled = false;
    }

    /**
     * 反序列化构造函数
     */
    public RandomSpawnSavedData(boolean randomSpawnEnabled) {
        this.randomSpawnEnabled = randomSpawnEnabled;
    }

    /**
     * 获取或创建服务器全局的持久化数据，并刷新内存镜像
     * <p>
     * 使用 {@link MinecraftServer#getDataStorage()} 挂载到服务器（全局，不绑定具体维度）。
     *
     * @param server 当前服务端实例
     * @return 持久化数据（已从磁盘恢复）
     */
    public static RandomSpawnSavedData get(MinecraftServer server) {
        RandomSpawnSavedData data = server.getDataStorage().computeIfAbsent(TYPE);
        // 同步内存镜像，确保读路径立即反映磁盘状态
        cachedEnabled = data.randomSpawnEnabled;
        return data;
    }

    /**
     * 读取开关状态（零开销内存读）
     * <p>
     * 供 {@link CombatEventHandler#onEntityJoinLevel} 等高频路径调用。
     * 服务端尚未启动 / 数据尚未加载时返回 {@code false}（默认值）。
     *
     * @return {@code true} 表示自然刷新使用权重表随机等级/评级
     */
    public static boolean isEnabled() {
        return cachedEnabled;
    }

    /**
     * 设置开关状态（同步更新内存镜像与持久化数据）
     *
     * @param server  当前服务端实例
     * @param enabled 新状态
     */
    public static void setEnabled(MinecraftServer server, boolean enabled) {
        RandomSpawnSavedData data = get(server);
        data.randomSpawnEnabled = enabled;
        data.setDirty();
        // 同步内存镜像，保证高频读路径立即看到新值
        cachedEnabled = enabled;
    }
}
