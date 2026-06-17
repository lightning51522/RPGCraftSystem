package com.rpgcraft.core.profession.api;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 职业接口 —— 定义 RPG 职业的核心契约
 * <p>
 * 每个职业提供一组属性加成。加成由 {@link #getBaseBonusMap()}（基础值，1 级）和
 * {@link #getBonusPerLevel(Identifier)}（每级增量）共同决定，任意等级的加成由
 * {@link #getBonusAtLevel(Identifier, int)} 计算：{@code base + perLevel × (level - 1)}。
 * <p>
 * 职业分两类（见 {@link ProfessionType}）：
 * <ul>
 *   <li><b>主职业</b>（PRIMARY）：构成进阶树，{@link #getPrerequisite()} 链最终追溯到 {@code commoner}
 *       （平民，唯一根）。只有前置职业达到 {@link #getMaxLevel()} 满级才能进阶。</li>
 *   <li><b>副职业</b>（SECONDARY）：独立成树，{@link #getPrerequisite()} 只能指向其他副职业或为 null。
 *       副职业可被玩家选为"当前副职业"以提供被动加成，但不能做主职业。</li>
 * </ul>
 * <p>
 * {@link #getBonusMap()} 保留为 {@link #getBaseBonusMap()} 的别名，向后兼容。
 * <p>
 * 子模组可通过 {@link IProfessionProvider} SPI 注册自定义职业，或通过 datapack JSON 定义（见 profession 模块）。
 */
public interface IProfession {

    /**
     * 职业类型：决定该职业在职业树中的归属、可否做主/副职业。
     */
    enum ProfessionType {
        /** 主职业：进阶树成员，链根于 commoner；可 advance / switchMain */
        PRIMARY,
        /** 副职业：独立成树；可被选为当前副职业，不可做主职业 */
        SECONDARY
    }

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
     * 获取职业类型（主职业 / 副职业）
     *
     * @return 职业类型，默认 {@link ProfessionType#PRIMARY}
     */
    default ProfessionType getType() {
        return ProfessionType.PRIMARY;
    }

    /**
     * 判断是否为主职业
     * <p>
     * 等价于 {@code getType() == ProfessionType.PRIMARY}。
     *
     * @return {@code true} 表示主职业
     * @deprecated 使用 {@link #getType()} 替代，便于区分主/副职业
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    default boolean isPrimary() {
        return getType() == ProfessionType.PRIMARY;
    }

    /**
     * 获取进阶前置职业的标识符
     * <p>
     * 返回 null 表示该职业是树起点（无前置，如主职业树的平民、副职业树的根）。
     * 返回非 null 表示进阶自某基础职业（如狂战士返回战士 ID）。
     * <p>
     * 类型约束（由加载器校验）：
     * <ul>
     *   <li>主职业的 prerequisite 链最终必须追溯到 {@code commoner}</li>
     *   <li>副职业的 prerequisite 只能为 null 或指向其他副职业</li>
     * </ul>
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
     * 该职业专属经验表（可选）
     * <p>
     * 数组索引 i 对应从 {@code i+1} 级升到 {@code i+2} 级所需经验，长度应为 {@link #getMaxLevel()}-1。
     * 返回 null 表示使用全局默认公式（见 {@code ProfessionManager}）。
     *
     * @return 经验表数组，或 null 表示用全局公式
     */
    default int @Nullable [] getExpTable() {
        return null;
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
