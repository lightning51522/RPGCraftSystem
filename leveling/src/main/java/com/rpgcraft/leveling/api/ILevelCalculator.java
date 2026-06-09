package com.rpgcraft.leveling.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 等级经验计算策略接口
 * <p>
 * 定义玩家击杀怪物时的经验获取计算公式。默认实现为
 * {@link com.rpgcraft.leveling.DefaultLevelCalculator}。
 * <p>
 * 其他模组可以替换此实现来提供自定义的经验公式（如考虑距离、时间、队伍等因素），
 * 通过 {@link com.rpgcraft.leveling.LevelManager#setLevelCalculator(ILevelCalculator)} 注入。
 */
public interface ILevelCalculator {

    /**
     * 计算玩家击杀怪物时获得的经验值
     *
     * @param killer   击杀怪物的玩家
     * @param victim   被击杀的怪物实体
     * @param mobLevel 怪物等级（来自配置）
     * @param baseExp  怪物基础经验（来自配置）
     * @return 实际获得的经验值，≤ 0 表示不获得经验
     */
    int calculateExperienceGain(ServerPlayer killer, LivingEntity victim,
                                int mobLevel, int baseExp);
}
