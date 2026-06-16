package com.rpgcraft.core.registry;

import net.minecraft.server.level.ServerPlayer;

/**
 * 等级系统接口
 * <p>
 * 由等级模块注册实现，提供玩家等级和经验查询能力。
 * 供其他模块（如 UI 插件、命令系统）查询玩家等级数据。
 *
 * @see RPGSystems#registerLevelSystem(ILevelSystem)
 * @see RPGSystems#getLevelSystem()
 */
public interface ILevelSystem {

    /**
     * 获取玩家等级
     *
     * @param player 服务端玩家
     * @return 当前等级
     */
    int getLevel(ServerPlayer player);

    /**
     * 获取玩家经验值
     *
     * @param player 服务端玩家
     * @return 当前经验值
     */
    int getExperience(ServerPlayer player);

    /**
     * 设置玩家等级
     *
     * @param player 服务端玩家
     * @param level  目标等级
     */
    void setLevel(ServerPlayer player, int level);

    /**
     * 设置玩家经验值
     *
     * @param player     服务端玩家
     * @param experience 经验值
     */
    void setExperience(ServerPlayer player, int experience);

    /**
     * 增加经验并自动处理升级
     *
     * @param player 服务端玩家
     * @param amount 经验量
     * @return 是否发生了升级
     */
    boolean addExperience(ServerPlayer player, int amount);

    /**
     * 查询升到下一级所需经验
     *
     * @param player 服务端玩家
     * @return 升级所需经验，达到最大等级时返回 -1
     */
    int getExpForNextLevel(ServerPlayer player);

    /**
     * 查询经验表：从 {@code level} 升到 {@code level+1} 所需经验
     * <p>
     * 与玩家实例无关的静态经验表查询，供其他模块（如职业经验池计算）反算升级经验。
     *
     * @param level 当前等级（1-based）
     * @return 该级升级所需经验；level &lt; 1 或 &ge; 最大等级时返回 -1
     */
    int getExpForLevel(int level);

    /**
     * 获取玩家等级上限
     *
     * @return 最大等级
     */
    int getMaxLevel();

    /**
     * 同步等级数据到客户端
     *
     * @param player 服务端玩家
     */
    void syncToClient(ServerPlayer player);
}
