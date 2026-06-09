package com.rpgcraft.core.attribute.api;

import net.minecraft.resources.Identifier;

import java.util.Collection;

/**
 * RPG 属性值的基础接口
 * <p>
 * 定义属性值的读写契约。每个 RPG 属性（生命、力量、暴击率等）都通过此接口访问。
 * 默认实现为 {@link com.rpgcraft.core.attribute.EntityAttribute}。
 * <p>
 * 属性值通过<b>修饰符管线</b>计算：
 * <ol>
 *   <li>基础值（baseValue）作为管线起点</li>
 *   <li>累加所有 {@link Operation#ADDITION} 修饰符</li>
 *   <li>乘算 {@link Operation#MULTIPLY_BASE}</li>
 *   <li>乘算 {@link Operation#MULTIPLY_TOTAL}</li>
 *   <li>边界截断</li>
 * </ol>
 * {@link #getValue()} 返回管线计算后的最终值。
 * {@link #setValue(int)} 直接设置管线计算结果（用于战斗伤害扣除等场景）。
 */
public interface IAttribute {

    String getName();

    /**
     * 获取属性最终值（经管线计算）
     * <p>
     * 此值为只读的计算结果：baseValue + 所有修饰符 → 管线 → 截断后的值。
     */
    int getValue();

    /**
     * 直接设置属性的最终值
     * <p>
     * 用于战斗伤害扣除、治疗等直接覆盖场景。
     * 此值会被钳制到 [0, maxValue] 范围内。
     * <p>
     * <b>注意：</b>此方法设置的是管线计算后的最终值，不是基础值。
     * 下一帧调用 {@link #getValue()} 时会重新通过管线计算，
     * 因此直接设置值适用于"临时覆盖"，修饰符的变化会在下次计算时自动生效。
     */
    void setValue(int value);

    /**
     * 获取属性基础上限值（经管线计算）
     */
    int getMaxValue();

    /**
     * 设置属性基础上限值
     */
    void setMaxValue(int max);

    /**
     * 是否有上限
     *
     * @return {@code true} 如果 maxValue &lt; Integer.MAX_VALUE
     */
    boolean hasMaxValue();

    /**
     * 将管线计算结果直接设为最大值
     */
    void fillMax();

    // ====================================================================
    // 基础值（管线起点）
    // ====================================================================

    /**
     * 获取属性基础值
     * <p>
     * 这是管线计算的起点，不包含任何修饰符。
     */
    int getBaseValue();

    /**
     * 设置属性基础值
     * <p>
     * 用于等级提升、种族加成等永久性修改。修饰符的加成会在管线中自动叠加。
     *
     * @param value 新的基础值
     */
    void setBaseValue(int value);

    /**
     * 获取基础上限值（管线起点）
     */
    int getBaseMaxValue();

    /**
     * 设置基础上限值
     */
    void setBaseMaxValue(int max);

    // ====================================================================
    // 修饰符管理
    // ====================================================================

    /**
     * 添加修饰符
     * <p>
     * 修饰符会参与管线计算，影响 {@link #getValue()} 的结果。
     * 若已存在相同 sourceId + operation 的修饰符，将覆盖旧值。
     *
     * @param modifier 修饰符实例
     */
    void addModifier(IAttributeModifier modifier);

    /**
     * 移除指定来源的所有修饰符
     * <p>
     * 通常在装备脱下、职业切换时调用，一次性移除某个来源的所有加成。
     *
     * @param sourceId 来源标识符
     */
    void removeModifier(Identifier sourceId);

    /**
     * 获取所有修饰符
     *
     * @return 修饰符集合（只读视图）
     */
    Collection<IAttributeModifier> getModifiers();
}
