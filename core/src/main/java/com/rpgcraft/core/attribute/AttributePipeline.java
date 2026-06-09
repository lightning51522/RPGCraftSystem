package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.attribute.AttributePostAdditionEvent;
import com.rpgcraft.core.event.attribute.AttributeFinalizeEvent;
import net.minecraft.resources.Identifier;

import java.util.Collection;

/**
 * 属性管线计算工具
 * <p>
 * 纯静态工具类，执行属性值的管线计算。当 {@link EntityAttribute#getValue()} 或
 * {@link EntityAttribute#getMaxValue()} 被调用时，委托此类进行计算。
 * <p>
 * 管线步骤：
 * <ol>
 *   <li>以 baseValue 为起点</li>
 *   <li>累加所有 {@link Operation#ADDITION} 修饰符</li>
 *   <li>触发 {@link AttributePostAdditionEvent}（允许子模块插手加算结果）</li>
 *   <li>计算 {@link Operation#MULTIPLY_BASE}：value * (1 + sum(multiplier / 100.0))</li>
 *   <li>计算 {@link Operation#MULTIPLY_TOTAL}：value * (1 + sum(multiplier / 100.0))</li>
 *   <li>触发 {@link AttributeFinalizeEvent}（允许最终覆盖）</li>
 *   <li>边界截断 Math.max(0, value)</li>
 * </ol>
 */
public final class AttributePipeline {

    private AttributePipeline() {} // 禁止实例化

    /**
     * 计算属性最终值
     *
     * @param attributeId 属性标识符（用于事件）
     * @param baseValue   基础值
     * @param modifiers   修饰符集合
     * @return 经管线计算后的最终值（已截断为 ≥ 0）
     */
    public static int compute(Identifier attributeId, int baseValue,
                              Collection<IAttributeModifier> modifiers) {
        if (modifiers.isEmpty()) {
            return Math.max(0, baseValue);
        }

        // 1. 以基础值为起点
        double value = baseValue;

        // 2. 累加所有 ADDITION 修饰符
        for (IAttributeModifier mod : modifiers) {
            if (mod.getOperation() == Operation.ADDITION) {
                value += mod.getValue();
            }
        }

        // 3. 触发 AttributePostAdditionEvent
        AttributePostAdditionEvent postAddEvent = new AttributePostAdditionEvent(attributeId, (int) value);
        RPGEventBus.post(postAddEvent);
        value = postAddEvent.getValue();

        // 4. 计算 MULTIPLY_BASE：value * (1 + sum(multiplier / 100.0))
        double baseMultiplierSum = 0;
        for (IAttributeModifier mod : modifiers) {
            if (mod.getOperation() == Operation.MULTIPLY_BASE) {
                baseMultiplierSum += mod.getValue() / 100.0;
            }
        }
        value *= (1.0 + baseMultiplierSum);

        // 5. 计算 MULTIPLY_TOTAL：value * (1 + sum(multiplier / 100.0))
        double totalMultiplierSum = 0;
        for (IAttributeModifier mod : modifiers) {
            if (mod.getOperation() == Operation.MULTIPLY_TOTAL) {
                totalMultiplierSum += mod.getValue() / 100.0;
            }
        }
        value *= (1.0 + totalMultiplierSum);

        // 6. 触发 AttributeFinalizeEvent
        AttributeFinalizeEvent finalizeEvent = new AttributeFinalizeEvent(attributeId, (int) value);
        RPGEventBus.post(finalizeEvent);
        value = finalizeEvent.getValue();

        // 7. 边界截断
        return Math.max(0, (int) value);
    }
}
