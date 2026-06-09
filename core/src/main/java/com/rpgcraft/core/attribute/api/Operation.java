package com.rpgcraft.core.attribute.api;

/**
 * 属性修饰符的操作类型
 * <p>
 * 定义三种标准数学操作，强制第三方通过填数据而非写代码来实现 90% 的属性交互。
 * <p>
 * 管线计算顺序：ADDITION → MULTIPLY_BASE → MULTIPLY_TOTAL
 * <ul>
 *   <li>{@link #ADDITION} — 加算：直接加到属性基础值上</li>
 *   <li>{@link #MULTIPLY_BASE} — 基础乘算：基于加算后的值进行百分比乘算</li>
 *   <li>{@link #MULTIPLY_TOTAL} — 最终乘算：基于所有乘算后的值进行最终百分比乘算</li>
 * </ul>
 */
public enum Operation {

    /**
     * 加算操作
     * <p>
     * 在管线第一阶段执行，将所有 ADDITION 修饰符的值累加到基础值上。
     * 示例：装备 +10 力量 → ADDITION(10)
     */
    ADDITION,

    /**
     * 基础乘算操作
     * <p>
     * 在加算之后执行，将修饰符值视为百分比（值 / 100.0）进行乘算。
     * 公式：value * (1 + sum(multiplier / 100.0))
     * 示例：天赋 +20% 力量 → MULTIPLY_BASE(20)
     */
    MULTIPLY_BASE,

    /**
     * 最终乘算操作
     * <p>
     * 在基础乘算之后执行，对已计算过的总值再次进行百分比乘算。
     * 公式：value * (1 + sum(multiplier / 100.0))
     * 示例：狂暴 buff +50% 总攻击 → MULTIPLY_TOTAL(50)
     */
    MULTIPLY_TOTAL
}
