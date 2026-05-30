package com.rpgcraft.core.attribute.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.List;

/**
 * 属性注册中心
 * <p>
 * 提供属性的注册、查询和遍历能力。
 * 默认实现为 {@link com.rpgcraft.core.attribute.DefaultAttributeRegistry}。
 * <p>
 * 其他模组通过 {@link IAttributeProvider#registerAttributes(IAttributeRegistry)}
 * 获取注册中心实例并注册自定义属性。
 */
public interface IAttributeRegistry {

    /**
     * 注册一个新属性
     *
     * @param id            属性的唯一标识符（如 rpgcraftcore:life）
     * @param displayName   HUD/命令中的显示名称
     * @param defaultValue  属性默认值
     * @param defaultMaxValue 属性默认上限值（Integer.MAX_VALUE 表示无上限）
     */
    void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue);

    /**
     * 通过 ID 查询属性条目
     *
     * @param id 属性标识符
     * @return 属性条目，未找到返回 null
     */
    IAttributeEntry getEntry(Identifier id);

    /**
     * 通过 ID 查找 AttachmentType
     * <p>
     * 用于网络包反序列化时查找客户端对应的 AttachmentType。
     *
     * @param id 属性标识符
     * @return 对应的 AttachmentType，未找到返回 null
     */
    AttachmentType<IAttribute> getTypeById(Identifier id);

    /**
     * 获取所有已注册的属性条目
     * <p>
     * 用于批量操作：登录同步、命令列表、HUD 渲染等。
     */
    List<IAttributeEntry> getAllEntries();

    /**
     * 便捷方法：从实体上读取指定属性
     *
     * @param entity 目标实体
     * @param id     属性标识符
     * @return 属性实例，未找到返回 null
     */
    IAttribute getAttribute(LivingEntity entity, Identifier id);
}
