package com.rpgcraft.core.snapshot;

import net.minecraft.server.level.ServerPlayer;

/**
 * 快照贡献者接口
 * <p>
 * 每个需要参与死亡/重生数据恢复的模块实现此接口，
 * 并在初始化时通过 {@link SnapshotCoordinator#registerContributor(ISnapshotContributor)} 注册。
 * <p>
 * 快照数据使用泛型 {@link Object} 类型，由各贡献者自行决定数据格式（record、map 等）。
 */
public interface ISnapshotContributor {

    /**
     * 获取贡献者的唯一标识符
     * <p>
     * 用于日志和调试，应使用模块的命名空间（如 "rpgcraftcore:attributes"）。
     *
     * @return 贡献者标识符字符串
     */
    String getContributorId();

    /**
     * 捕获快照数据
     * <p>
     * 在玩家死亡时调用。返回的快照对象会在 {@link #restoreSnapshot} 中原样传回。
     *
     * @param player 即将死亡的玩家
     * @return 包含快照数据的对象（可为任意类型，由实现者定义）
     */
    Object captureSnapshot(ServerPlayer player);

    /**
     * 从快照恢复数据
     * <p>
     * 在玩家克隆（PlayerEvent.Clone）时调用，将快照数据恢复到新实体。
     *
     * @param newPlayer 重生后的新玩家实体
     * @param data      死亡时捕获的快照对象（即 {@link #captureSnapshot} 的返回值）
     * @param mode      恢复模式
     */
    void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode);

    /**
     * 同步数据到客户端
     * <p>
     * 在玩家重生（PlayerRespawnEvent）后调用，将恢复的数据同步到客户端。
     *
     * @param player 重生后的玩家
     */
    void syncToClient(ServerPlayer player);
}
