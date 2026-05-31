package com.rpgcraft.core.attribute.api;

/**
 * 伤害类型接口
 * <p>
 * 将 {@link com.rpgcraft.core.attribute.AttackType} 提升为可扩展的接口，
 * 允许其他模组定义自定义伤害类型。
 */
public interface IDamageType {

    /**
     * 伤害类型名称
     */
    String getName();

    /**
     * 是否为物理伤害类型
     */
    boolean isPhysical();

    /**
     * 是否为法术伤害类型
     */
    boolean isMagic();
}
