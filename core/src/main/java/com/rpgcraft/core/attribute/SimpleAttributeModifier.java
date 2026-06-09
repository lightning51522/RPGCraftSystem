package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import net.minecraft.resources.Identifier;

/**
 * {@link IAttributeModifier} 的简单不可变实现
 * <p>
 * 使用 record 存储 sourceId、operation、value 三个字段，
 * 用于 {@link EntityAttributeAttachment} 内部的修饰符存储和序列化。
 * <p>
 * 可被其他模块复用（如装备模块创建修饰符），
 * 替代直接匿名实现 {@code new IAttributeModifier() { ... }} 的写法。
 *
 * @see IAttributeModifier
 * @see EntityAttributeAttachment
 */
public record SimpleAttributeModifier(
        Identifier sourceId,
        Operation operation,
        int value
) implements IAttributeModifier {

    @Override
    public Identifier getSourceId() {
        return sourceId;
    }

    @Override
    public Operation getOperation() {
        return operation;
    }

    @Override
    public int getValue() {
        return value;
    }
}
