package com.rpgcraft.core.attribute.api;

/**
 * RPG 属性值的基础接口
 * <p>
 * 定义属性值的读写契约。每个 RPG 属性（生命、力量、暴击率等）都通过此接口访问。
 * 默认实现为 {@link com.rpgcraft.core.attribute.EntityAttribute}。
 */
public interface IAttribute {

    String getName();
    /**
     * 获取属性当前值
     */
    int getValue();

    /**
     * 设置属性当前值
     * <p>
     * 实现应保证值在 [0, maxValue] 范围内。
     */
    void setValue(int value);

    /**
     * 获取属性上限值
     * <p>
     * 当返回 {@link Integer#MAX_VALUE} 时表示无上限。
     */
    int getMaxValue();

    /**
     * 设置属性上限值
     */
    void setMaxValue(int max);

    /**
     * 是否有上限
     *
     * @return {@code true} 如果 maxValue &lt; Integer.MAX_VALUE
     */
    boolean hasMaxValue();
}
