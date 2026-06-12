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
     * 注册一个新属性（完整参数，含说明文字）—— 注册链的最终抽象方法
     * <p>
     * 其他 {@code register(...)} 重载最终委托到此方法（说明文字默认传空字符串）。
     *
     * @param id                   属性的唯一标识符（如 rpgcraftcore:life）
     * @param displayName          HUD/命令中的显示名称
     * @param description          属性说明文字（角色界面悬停 tooltip，空字符串表示无说明）
     * @param defaultValue         属性默认值
     * @param defaultMaxValue      属性默认上限值（Integer.MAX_VALUE 表示无上限）
     * @param resetOnRespawn       重生时是否恢复到最大值（资源型属性如生命、技力、法力为 true）
     * @param equipmentAffectsMax  装备加成是否同时影响上限值
     */
    void register(Identifier id, String displayName, String description,
                  int defaultValue, int defaultMaxValue,
                  boolean resetOnRespawn, boolean equipmentAffectsMax);

    /**
     * 注册一个新属性（说明文字默认为空）
     *
     * @param id              属性的唯一标识符（如 rpgcraftcore:life）
     * @param displayName     HUD/命令中的显示名称
     * @param defaultValue    属性默认值
     * @param defaultMaxValue 属性默认上限值（Integer.MAX_VALUE 表示无上限）
     */
    default void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue) {
        register(id, displayName, "", defaultValue, defaultMaxValue, false, false);
    }

    /**
     * 注册一个新属性（指定重生行为，说明文字默认为空）
     *
     * @param id              属性的唯一标识符
     * @param displayName     HUD/命令中的显示名称
     * @param defaultValue    属性默认值
     * @param defaultMaxValue 属性默认上限值（Integer.MAX_VALUE 表示无上限）
     * @param resetOnRespawn  重生时是否恢复到最大值（资源型属性如生命、技力、法力为 true）
     */
    default void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue, boolean resetOnRespawn) {
        register(id, displayName, "", defaultValue, defaultMaxValue, resetOnRespawn, false);
    }

    /**
     * 注册一个新属性（指定重生行为和装备影响上限标志，说明文字默认为空）
     *
     * @param id                   属性的唯一标识符
     * @param displayName          HUD/命令中的显示名称
     * @param defaultValue         属性默认值
     * @param defaultMaxValue      属性默认上限值（Integer.MAX_VALUE 表示无上限）
     * @param resetOnRespawn       重生时是否恢复到最大值
     * @param equipmentAffectsMax  装备加成是否同时影响上限值
     */
    default void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue, boolean resetOnRespawn, boolean equipmentAffectsMax) {
        register(id, displayName, "", defaultValue, defaultMaxValue, resetOnRespawn, equipmentAffectsMax);
    }

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

    /**
     * 一次性捕获实体上所有已注册属性的快照
     * <p>
     * 将每个属性的 currentValue 和 maxValue 保存为不可变映射，
     * 可安全缓存用于后续恢复（如死亡→重生场景）。
     *
     * @param entity 目标实体
     * @return 包含所有属性值的快照
     */
    AttributeSnapshot createSnapshot(LivingEntity entity);

    /**
     * 将实体上所有已注册属性重置为注册时的默认值
     * <p>
     * 每个属性的 maxValue 恢复为 defaultMaxValue，currentValue 恢复为 defaultValue。
     *
     * @param entity 目标实体
     */
    void resetToDefaults(LivingEntity entity);

    /**
     * 将快照中的属性值恢复到实体上
     * <p>
     * 恢复规则：
     * <ul>
     *   <li>所有属性：先恢复 maxValue</li>
     *   <li>资源型属性（{@code shouldResetOnRespawn=true}）：currentValue 设为 maxValue</li>
     *   <li>能力型属性：恢复快照中的 currentValue</li>
     * </ul>
     *
     * @param entity   目标实体（新实体）
     * @param snapshot 之前通过 {@link #createSnapshot} 创建的快照
     */
    void applySnapshot(LivingEntity entity, AttributeSnapshot snapshot);
}
