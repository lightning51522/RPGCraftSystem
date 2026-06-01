package com.rpgcraft.core.level;

import com.rpgcraft.core.level.api.ILevelCalculator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@link ILevelCalculator} 的默认实现
 * <p>
 * 经验公式：{@code 实际经验 = sqrt(怪物等级 / 玩家等级) * 基础经验}
 * <p>
 * 该公式在高等级打低等级怪时经验减少，低等级打高等级怪时经验增加，
 * 鼓励玩家挑战高于自身等级的怪物。
 * <ul>
 *   <li>玩家 1 级，怪物 1 级，基础经验 100 → 获得 100 经验</li>
 *   <li>玩家 10 级，怪物 1 级，基础经验 100 → 获得 ~31 经验（衰减）</li>
 *   <li>玩家 1 级，怪物 10 级，基础经验 100 → 获得 ~316 经验（奖励）</li>
 * </ul>
 */
public class DefaultLevelCalculator implements ILevelCalculator {

    @Override
    public int calculateExperienceGain(ServerPlayer killer, LivingEntity victim,
                                       int mobLevel, int baseExp) {
        PlayerLevelData levelData = killer.getData(LevelManager.PLAYER_LEVEL);
        int playerLevel = Math.max(1, levelData.getLevel());

        return (int) (Math.sqrt((double) mobLevel / playerLevel) * baseExp);
    }
}
