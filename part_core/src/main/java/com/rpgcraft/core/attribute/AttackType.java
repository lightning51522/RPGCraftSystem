package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IDamageType;

/**
 * 攻击/伤害类型枚举
 * <p>
 * 实现 {@link IDamageType} 接口，定义模组中所有伤害的分类方式。
 * 用于 {@link com.rpgcraft.core.attribute.api.IDamageCalculator} 中的伤害计算。
 * <p>
 * <b>当前已实现的类型：</b>
 * <ul>
 *   <li>{@link #PHYSICAL} —— 纯物理伤害，受防御力减免</li>
 *   <li>{@link #MAGIC} —— 纯法术伤害，受法术抗性百分比减免</li>
 * </ul>
 * <p>
 * <b>暂未实现的混合类型：</b>
 * <ul>
 *   <li>{@link #PHYSICAL_WITH_MAGIC} —— 物理为主附带法术</li>
 *   <li>{@link #MAGIC_WITH_PHYSICAL} —— 法术为主附带物理</li>
 *   <li>{@link #MIX_TYPE} —— 混合伤害</li>
 * </ul>
 */
public enum AttackType implements IDamageType {
    /** 纯物理伤害：计算时减去目标防御力 */
    PHYSICAL,
    /** 纯法术伤害：计算时减去目标法术抗性对应的百分比 */
    MAGIC,
    /** 物理为主附带法术（暂未实现） */
    PHYSICAL_WITH_MAGIC,
    /** 法术为主附带物理（暂未实现） */
    MAGIC_WITH_PHYSICAL,
    /** 混合伤害（暂未实现） */
    MIX_TYPE;

    @Override
    public String getName() {
        return name().toLowerCase();
    }

    @Override
    public boolean isPhysical() {
        return this == PHYSICAL || this == PHYSICAL_WITH_MAGIC;
    }

    @Override
    public boolean isMagic() {
        return this == MAGIC || this == MAGIC_WITH_PHYSICAL;
    }
}
