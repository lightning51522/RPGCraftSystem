package com.rpgcraft.core.event.attribute;

import com.rpgcraft.core.event.RPGEvent;
import net.minecraft.resources.Identifier;

/**
 * 属性管线事件：最终覆盖
 * <p>
 * 在所有乘算完成后、边界截断前触发。
 * 子模块可监听此事件对最终值做最后的覆盖或调整。
 * <p>
 * 使用示例：
 * <pre>{@code
 * RPGEventBus.register(AttributeFinalizeEvent.class, event -> {
 *     // 真实伤害：确保最终伤害不低于某个最小值
 *     if (event.getAttributeId().equals(LIFE_ID)) {
 *         event.setValue(Math.min(event.getValue(), capValue));
 *     }
 * }, RPGEvent.PRIORITY_LATE);
 * }</pre>
 */
public class AttributeFinalizeEvent extends RPGEvent {

    private final Identifier attributeId;
    private int value;

    /**
     * @param attributeId 属性标识符
     * @param value       乘算后的值（截断前）
     */
    public AttributeFinalizeEvent(Identifier attributeId, int value) {
        this.attributeId = attributeId;
        this.value = value;
    }

    /** 获取属性标识符 */
    public Identifier getAttributeId() {
        return attributeId;
    }

    /** 获取乘算后的值 */
    public int getValue() {
        return value;
    }

    /** 覆盖最终值（截断前） */
    public void setValue(int value) {
        this.value = value;
    }
}
