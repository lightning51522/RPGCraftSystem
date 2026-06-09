package com.rpgcraft.core.snapshot;

import com.rpgcraft.core.RPGCraftCore;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 快照协调器
 * <p>
 * 管理 {@link ISnapshotContributor} 注册表，协调死亡/克隆/重生时的数据快照捕获和恢复。
 * <p>
 * <h3>生命周期：</h3>
 * <ol>
 *   <li>玩家死亡 → {@link #captureIfDying(ServerPlayer)} / {@link #captureDeath(ServerPlayer)} 收集快照</li>
 *   <li>玩家克隆 → {@link #restoreOnClone(ServerPlayer)} 恢复快照数据到新实体</li>
 *   <li>玩家重生 → {@link #syncOnRespawn(ServerPlayer)} 同步数据到客户端</li>
 *   <li>玩家登出 → {@link #cleanup(UUID)} 清理内存中的快照</li>
 * </ol>
 * <p>
 * <h3>线程安全：</h3>
 * 使用 {@link ConcurrentHashMap} 和 {@link LinkedHashMap} 保证线程安全。
 */
public class SnapshotCoordinator {

    /** 已注册的快照贡献者，按注册顺序排列 */
    private static final Map<String, ISnapshotContributor> contributors = new LinkedHashMap<>();

    /** 死亡快照缓存：玩家 UUID → 贡献者 ID → 快照数据 */
    private static final Map<UUID, Map<String, Object>> deathSnapshots = new ConcurrentHashMap<>();

    /**
     * 注册快照贡献者
     *
     * @param contributor 贡献者实例
     */
    public static void registerContributor(ISnapshotContributor contributor) {
        contributors.put(contributor.getContributorId(), contributor);
        RPGCraftCore.LOGGER.debug("快照贡献者已注册: {}", contributor.getContributorId());
    }

    /**
     * 获取所有已注册的贡献者
     */
    public static Collection<ISnapshotContributor> getContributors() {
        return Collections.unmodifiableCollection(contributors.values());
    }

    /**
     * 检测玩家生命是否归零，如果是则立即捕获全量快照
     * <p>
     * 在生命属性可能变为 0 的所有位置调用（指令设置、战斗伤害同步等），
     * 确保在游戏处理角色死亡之前捕获所有数据。
     * 使用 {@code putIfAbsent} 避免覆盖已有的快照（只保存第一次归零时的值）。
     *
     * @param player 可能即将死亡的玩家
     */
    public static void captureIfDying(ServerPlayer player) {
        var lifeAttr = player.getData(com.rpgcraft.core.attribute.AttributeManager.LIFE);
        if (lifeAttr.getValue() <= 0) {
            captureDeath(player);
        }
    }

    /**
     * 捕获玩家死亡时的全量快照
     * <p>
     * 遍历所有已注册的 {@link ISnapshotContributor}，收集每个贡献者的快照数据。
     * 使用 {@code putIfAbsent} 避免覆盖已有的快照。
     *
     * @param player 即将死亡的玩家
     */
    public static void captureDeath(ServerPlayer player) {
        Map<String, Object> snapshot = deathSnapshots.computeIfAbsent(
                player.getUUID(), k -> new LinkedHashMap<>());

        // 如果已有快照（由 captureIfDying 提前捕获），跳过重复捕获
        if (!snapshot.isEmpty()) return;

        for (ISnapshotContributor contributor : contributors.values()) {
            try {
                Object data = contributor.captureSnapshot(player);
                if (data != null) {
                    snapshot.put(contributor.getContributorId(), data);
                }
            } catch (Exception e) {
                RPGCraftCore.LOGGER.error("快照捕获失败 [{}]: {}",
                        contributor.getContributorId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 在玩家克隆时恢复快照数据
     * <p>
     * 遍历所有已注册的 {@link ISnapshotContributor}，将快照数据恢复到新实体。
     * 恢复完成后自动清理快照缓存。
     *
     * @param newPlayer 重生后的新玩家实体
     */
    public static void restoreOnClone(ServerPlayer newPlayer) {
        Map<String, Object> snapshot = deathSnapshots.get(newPlayer.getUUID());
        if (snapshot == null) return;

        DeathRestoreMode mode = DeathRestoreMode.getCurrentMode();

        for (ISnapshotContributor contributor : contributors.values()) {
            Object data = snapshot.get(contributor.getContributorId());
            if (data == null) continue;

            try {
                contributor.restoreSnapshot(newPlayer, data, mode);
            } catch (Exception e) {
                RPGCraftCore.LOGGER.error("快照恢复失败 [{}]: {}",
                        contributor.getContributorId(), e.getMessage(), e);
            }
        }

        // 恢复完成后清理快照
        deathSnapshots.remove(newPlayer.getUUID());
    }

    /**
     * 在玩家重生后同步数据到客户端
     * <p>
     * PlayerRespawnEvent 在客户端已完成新实体创建之后触发，
     * 此时发送的同步包能被正确接收。
     *
     * @param player 重生后的玩家
     */
    public static void syncOnRespawn(ServerPlayer player) {
        for (ISnapshotContributor contributor : contributors.values()) {
            try {
                contributor.syncToClient(player);
            } catch (Exception e) {
                RPGCraftCore.LOGGER.error("快照同步失败 [{}]: {}",
                        contributor.getContributorId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 清理指定玩家的快照缓存
     * <p>
     * 在玩家断开连接时调用，防止长期运行服务器上的内存泄漏。
     *
     * @param playerUuid 玩家 UUID
     */
    public static void cleanup(UUID playerUuid) {
        deathSnapshots.remove(playerUuid);
    }
}
