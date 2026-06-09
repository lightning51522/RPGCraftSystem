package com.rpgcraft.core.attribute.api;

import com.rpgcraft.core.attribute.EmptyAttributeModifier;
import net.minecraft.resources.Identifier;

/**
 * 属性修饰符接口
 * <p>
 * 表示一个对属性的数学修饰，由操作类型 {@link Operation} 和数值定义。
 * 修饰符通过 {@link IAttribute#addModifier(IAttributeModifier)} 添加到属性上，
 * 由管线统一计算最终值。
 * <p>
 * 每个修饰符有一个唯一来源标识符 {@link #getSourceId()}，用于按来源批量移除。
 * 例如装备系统使用 {@code "rpgcraftequipment:chest_diamond_chestplate"} 标识某个装备槽的加成。
 * <p>
 * <b>优雅降级</b>：当从存档反序列化修饰符时，若属性 ID 在注册表中不存在
 * （所属模组被卸载），应返回 {@link #EMPTY} 而不是抛出异常。
 * {@link #EMPTY} 对管线计算无任何影响，重新安装模组后属性自动恢复。
 */
public interface IAttributeModifier {

    /**
     * 空修饰符常量（优雅降级用）
     * <p>
     * 当属性 ID 在注册表中不存在时（模组被卸载），反序列化应返回此常量。
     * 此修饰符的 {@link #getValue()} 为 0，{@link #getOperation()} 为 {@link Operation#ADDITION}，
     * 对管线计算无任何影响。
     *
     * @see EmptyAttributeModifier
     */
    IAttributeModifier EMPTY = EmptyAttributeModifier.INSTANCE;

    /**
     * 获取修饰符的来源标识符
     * <p>
     * 用于按来源移除一组修饰符（如脱下装备时移除该装备的所有加成）。
     * 来源标识符的命名规范：{@code "<modid>:<描述>"}
     *
     * @return 来源标识符
     */
    Identifier getSourceId();

    /**
     * 获取修饰符的操作类型
     *
     * @return 操作类型
     */
    Operation getOperation();

    /**
     * 获取修饰符的数值
     * <p>
     * 对于 {@link Operation#ADDITION}：直接加算值
     * 对于 {@link Operation#MULTIPLY_BASE} 和 {@link Operation#MULTIPLY_TOTAL}：百分比值（如 20 表示 +20%）
     *
     * @return 修饰符数值
     */
    int getValue();
}
