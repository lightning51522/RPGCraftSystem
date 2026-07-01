package com.rpgcraft.core.attribute.api;

import com.rpgcraft.core.ui.IAttributeRendererFactory;
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
     * 属性的说明文字
     * <p>
     * 用于角色界面中鼠标悬停在属性上时显示的原版风格 tooltip。
     * 默认为空字符串；为空时不显示 tooltip。支持以 {@code \n} 分隔的多行说明。
     */
    default String getDescription() {
        return "";
    }

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

    /**
     * 重生时是否恢复到最大值
     * <p>
     * 生命、技力、法力等"资源型"属性应在重生时恢复满值，
     * 而法抗、暴击率等"能力型"属性应保持不变。
     */
    boolean shouldResetOnRespawn();

    /**
     * 装备加成是否同时影响上限值
     * <p>
     * 为 true 时，装备加成同时改变 currentValue 和 maxValue；
     * 为 false 时，装备加成仅改变 currentValue。
     */
    boolean equipmentAffectsMax();

    /**
     * 获取属性的自定义渲染器工厂
     * <p>
     * 若返回 {@code null}，角色界面使用默认文本渲染器。
     * 渲染器工厂仅在客户端运行时调用。
     *
     * @return 渲染器工厂，若无自定义渲染器返回 {@code null}
     * @see com.rpgcraft.core.ui.IAttributeRendererFactory
     */
    default IAttributeRendererFactory getRendererFactory() {
        return null;
    }

    /**
     * 是否允许玩家分配属性点到此属性。
     * <p>
     * 默认返回 {@code !shouldResetOnRespawn()}：资源型属性（life / skill_point 等
     * 重生恢复类属性）不可加点，其余属性可加点。
     * 子类可覆写以支持更多不可加点属性（如暴击率/暴击伤害等综合派生属性）。
     *
     * @return {@code true} 表示可加点
     */
    default boolean isAllocatable() {
        return !shouldResetOnRespawn();
    }

    /**
     * 是否允许此属性作为宝石镶嵌词条出现。
     * <p>
     * <b>设计意图</b>：宝石系统的属性词条候选池由各属性注册方自行声明，而非由宝石模块
     * 用其它 flag（如 {@link #isAllocatable()}）推导。第三方属性模块只需在注册时通过
     * {@code register(..., availableAsAffix=true)} 显式开启，即可自动被宝石系统枚举为词条来源
     * （宝石模块通过 {@link IAttributeRegistry#getAllEntries()} 运行时枚举本 flag 为 true 的条目）。
     * <p>
     * <b>独立性</b>：本 flag 是纯查询语义，与加点、装备加成、重生恢复等行为完全正交。
     * 默认 {@code false}（保守）：避免生命等关键属性在未显式声明时被意外纳入词条池。
     * <b>当宝石模块未加载时，本方法没有任何调用方，对 core / attributes / equipment 等原流程零影响。</b>
     *
     * @return {@code true} 表示此属性可作宝石词条；默认 {@code false}
     */
    default boolean isAvailableAsAffix() {
        return false;
    }
}
