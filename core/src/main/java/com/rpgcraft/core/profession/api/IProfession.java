package com.rpgcraft.core.profession.api;

import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * 职业接口 —— 定义 RPG 职业的核心契约
 * <p>
 * 每个职业提供一组属性加成。加成由 {@link #getBaseBonusMap()}（基础值，1 级）和
 * {@link #getBonusPerLevel(Identifier)}（每级增量）共同决定，任意等级的加成由
 * {@link #getBonusAtLevel(Identifier, int)} 计算：{@code base + perLevel × (level - 1)}。
 * <p>
 * 职业可构成进阶树：{@link #getPrerequisite()} 返回前置职业 ID（为 null 表示起点职业，
 * 如平民）。只有前置职业达到 {@link #getMaxLevel()} 满级才能进阶。
 * <p>
 * {@link #getBonusMap()} 保留为 {@link #getBaseBonusMap()} 的别名，向后兼容。
 * <p>
 * 子模组可通过 {@link IProfessionProvider} SPI 注册自定义职业。
 */
public interface IProfession {

    /**
     * 获取职业的唯一标识符
     *
     * @return 职业标识符，如 {@code rpgcraftcore:warrior}
     */
    Identifier getId();

    /**
     * 获取职业的显示名称
     *
     * @return 显示名称，如 "战士"
     */
    String getDisplayName();

    /**
     * 获取职业的描述文本
     *
     * @return 描述，如 "力量提升，敏捷降低"
     */
    String getDescription();

    /**
     * 判断是否为主职业
     * <p>
     * 主职业返回 {@code true}，副职业返回 {@code false}。
     * （历史遗留：当前所有内置职业均为 true，副职业通过 ProfessionData.secondaryProfessionId 单独选择。）
     *
     * @return {@code true} 表示主职业
     */
    boolean isPrimary();

    /**
     * 获取进阶前置职业的标识符
     * <p>
     * 返回 null 表示该职业是树起点（无前置，如平民）。
     * 返回非 null 表示进阶自某基础职业（如战士的进阶职业狂战士返回战士 ID）。
     * 只有前置职业达到 {@link #getMaxLevel()} 满级才能解锁本职业。
     *
     * @return 前置职业 ID，或 null
     */
    default Identifier getPrerequisite() {
        return null;
    }

    /**
     * 是否为进阶职业（即有前置职业）
     */
    default boolean isAdvanced() {
        return getPrerequisite() != null;
    }

    /**
     * 获取职业等级上限
     *
     * @return 等级上限，默认 20
     */
    default int getMaxLevel() {
        return 20;
    }

    /**
     * 获取 1 级时（基础）的属性加成映射
     * <p>
     * 键为属性标识符，值为基础加成值（正增益/负减益）。应为不可变映射。
     * 默认委托给 {@link #getBonusMap()} 以兼容旧实现。
     *
     * @return 基础加成映射
     */
    default Map<Identifier, Integer> getBaseBonusMap() {
        return getBonusMap();
    }

    /**
     * 获取某属性在本职业下的每级增量
     *
     * @param attrId 属性标识符
     * @return 每级增量，默认 0（即加成不随等级变化）
     */
    default int getBonusPerLevel(Identifier attrId) {
        return 0;
    }

    /**
     * 计算某属性在指定等级时的加成值
     * <p>
     * 公式：{@code base + perLevel × (level - 1)}
     *
     * @param attrId 属性标识符
     * @param level  职业等级（≥ 1）
     * @return 该等级下的加成值
     */
    default int getBonusAtLevel(Identifier attrId, int level) {
        int base = getBaseBonusMap().getOrDefault(attrId, 0);
        int perLevel = getBonusPerLevel(attrId);
        return base + perLevel * (Math.max(1, level) - 1);
    }

    /**
     * 获取职业提供的属性加成映射（基础值）。
     * <p>
     * 历史方法，等价于 {@link #getBaseBonusMap()}。保留以兼容现有调用方与实现。
     *
     * @return 属性加成映射
     */
    Map<Identifier, Integer> getBonusMap();
}
