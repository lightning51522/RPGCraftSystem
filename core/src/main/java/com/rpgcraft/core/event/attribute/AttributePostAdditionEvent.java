package com.rpgcraft.core.event.attribute;

import com.rpgcraft.core.event.RPGEvent;
import net.minecraft.resources.Identifier;

/**
 * 属性管线事件：加算后
 * <p>
 * 在所有 {@link com.rpgcraft.core.attribute.api.Operation#ADDITION} 修饰符累加完成后触发。
 * 子模块可监听此事件修改加算后的中间值。
 * <p>
 * 使用示例：
 * <pre>{@code
 * RPGEventBus.register(AttributePostAdditionEvent.class, event -> {
 *     // 检查特定属性的加算结果并调整
 *     if (event.getAttributeId().equals(STRENGTH_ID)) {
 *         event.setValue(event.getValue() + extraBonus);
 *     }
 * }, RPGEvent.PRIORITY_NORMAL);
 * }</pre>
 */
public class AttributePostAdditionEvent extends RPGEvent {

    private final Identifier attributeId;
    private int value;

    /**
     * @param attributeId 属性标识符
     * @param value       加算后的中间值
     */
    public AttributePostAdditionEvent(Identifier attributeId, int value) {
        this.attributeId = attributeId;
        this.value = value;
    }

    /** 获取属性标识符 */
    public Identifier getAttributeId() {
        return attributeId;
    }

    /** 获取加算后的中间值 */
    public int getValue() {
        return value;
    }

    /** 修改加算后的中间值 */
    public void setValue(int value) {
        this.value = value;
    }
}
