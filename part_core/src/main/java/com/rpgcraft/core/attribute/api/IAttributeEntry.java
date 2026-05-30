package com.rpgcraft.core.attribute.api;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/**
 * 属性注册条目
 * <p>
 * 描述一个已注册属性的全部元信息，包括网络标识、显示名称、数据访问器和默认值。
 * 通过 {@link IAttributeRegistry#getAllEntries()} 获取所有已注册的条目。
 */
public interface IAttributeEntry {

    /**
     * 属性的网络标识符
     * <p>
     * 用于网络包中标识属性类型，以及命令中的属性查找。
     */
    Identifier getId();

    /**
     * 属性的显示名称
     * <p>
     * 用于 HUD 和命令输出中的人类可读名称。
     */
    String getDisplayName();

    /**
     * 属性的 AttachmentType 供应器
     * <p>
     * 用于从实体上读写属性数据：{@code entity.getData(entry.getSupplier())}。
     */
    Supplier<AttachmentType<IAttribute>> getSupplier();

    /**
     * 属性的默认值
     */
    int getDefaultValue();

    /**
     * 属性的默认上限值
     */
    int getDefaultMaxValue();

    /**
     * 是否为有上限的属性
     */
    boolean isCapped();
}
