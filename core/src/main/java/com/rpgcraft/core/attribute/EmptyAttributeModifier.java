package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import net.minecraft.resources.Identifier;

/**
 * 空属性修饰符实现（优雅降级用）
 * <p>
 * 当从存档反序列化修饰符时，若属性 ID 在注册表中不存在（所属模组被卸载），
 * 返回此无副作用的空实现，而不是抛出异常。
 * <p>
 * 此修饰符对管线计算无任何影响：
 * <ul>
 *   <li>{@link #getValue()} 返回 0</li>
 *   <li>{@link #getOperation()} 返回 {@link Operation#ADDITION}（0 + 0 = 无效果）</li>
 * </ul>
 * <p>
 * 重新安装模组后，原始修饰符可通过正常流程恢复。
 *
 * @see IAttributeModifier#EMPTY
 */
public final class EmptyAttributeModifier implements IAttributeModifier {

    /** 空修饰符的单例实例 */
    public static final EmptyAttributeModifier INSTANCE = new EmptyAttributeModifier();

    /** 空修饰符的来源标识符 */
    private static final Identifier EMPTY_SOURCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "empty");

    private EmptyAttributeModifier() {
        // 单例，禁止外部实例化
    }

    @Override
    public Identifier getSourceId() {
        return EMPTY_SOURCE_ID;
    }

    @Override
    public Operation getOperation() {
        // ADDITION + value=0 = 无效果
        return Operation.ADDITION;
    }

    @Override
    public int getValue() {
        return 0;
    }

    @Override
    public String toString() {
        return "EmptyAttributeModifier";
    }
}
