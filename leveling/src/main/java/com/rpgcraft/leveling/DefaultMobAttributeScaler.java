package com.rpgcraft.leveling;

import com.rpgcraft.core.level.api.IMobAttributeScaler;

/**
 * {@link IMobAttributeScaler} 的默认实现
 * <p>
 * 每级增加全属性 5%：
 * <ul>
 *   <li>等级 1 = 100%（基础值不变）</li>
 *   <li>等级 2 = 105%</li>
 *   <li>等级 3 = 110%</li>
 *   <li>等级 20 = 195%</li>
 * </ul>
 * 缩放后的值最低为 1。
 */
public class DefaultMobAttributeScaler implements IMobAttributeScaler {

    /** 每级增加的比例（0.05 = 5%） */
    private static final double PER_LEVEL_BONUS = 0.05;

    @Override
    public int scaleAttribute(int baseValue, int mobLevel, String attributeName) {
        double multiplier = 1.0 + (mobLevel - 1) * PER_LEVEL_BONUS;
        return Math.max(1, (int) (baseValue * multiplier));
    }
}
