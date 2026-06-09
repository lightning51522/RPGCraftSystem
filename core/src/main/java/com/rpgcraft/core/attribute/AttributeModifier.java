package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import net.minecraft.resources.Identifier;

/**
 * {@link IAttributeModifier} 的默认实现
 * <p>
 * 使用 record 类型保证不可变性和值语义。
 * 通过 {@link #of(Identifier, Operation, int)} 工厂方法创建实例。
 */
public record AttributeModifier(
        Identifier sourceId,
        Operation operation,
        int modifierValue
) implements IAttributeModifier {

    /**
     * 工厂方法：创建属性修饰符
     *
     * @param sourceId  来源标识符
     * @param operation 操作类型
     * @param value     数值
     * @return 新的修饰符实例
     */
    public static AttributeModifier of(Identifier sourceId, Operation operation, int value) {
        return new AttributeModifier(sourceId, operation, value);
    }

    @Override
    public Identifier getSourceId() { return sourceId; }

    @Override
    public Operation getOperation() { return operation; }

    @Override
    public int getValue() { return modifierValue; }
}
