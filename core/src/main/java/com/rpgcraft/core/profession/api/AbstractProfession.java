package com.rpgcraft.core.profession.api;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 职业抽象基类 —— 封装常用静态数据字段，降低职业子类样板代码
 * <p>
 * 继承本类的职业只需在构造函数传入核心字段（ID、显示名、描述、类型、前置、等级上限、加成映射），
 * 然后按需覆写 {@link IProfession} 的钩子方法（如 {@link #getIconItem}、{@link #onAttack} 等）。
 * <p>
 * 所有 Map 字段在构造时被冻结为不可变，保证运行时不可变。
 * <p>
 * 不继承本类、直接实现 {@link IProfession} 也是允许的（适合纯行为型职业或特殊场景）。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class WarriorProfession extends AbstractProfession {
 *     public WarriorProfession() {
 *         super(
 *             Identifier.fromNamespaceAndPath("rpgcraftcore", "warrior"),
 *             "战士", "力量提升，敏捷降低",
 *             ProfessionType.PRIMARY,
 *             ProfessionManager.COMMONER_ID,
 *             20,
 *             Map.of(STRENGTH_ID, 5, AGILE_ID, -3),
 *             Map.of(STRENGTH_ID, 1)
 *         );
 *     }
 *
 *     @Override public ItemStack getIconItem() { return new ItemStack(Items.IRON_SWORD); }
 *
 *     @Override
 *     public void onAttack(ProfessionCombatContext ctx) {
 *         // 战士特有战斗逻辑
 *     }
 * }
 * }</pre>
 *
 * @see IProfession
 */
public abstract class AbstractProfession implements IProfession {

    private final Identifier id;
    private final String displayName;
    private final String description;
    private final ProfessionType type;
    /** 单前置 ID（单前置职业的树形画线用）；复合职业为 null */
    private final @Nullable Identifier prerequisite;
    private final int maxLevel;
    private final Map<Identifier, Integer> baseBonuses;
    private final Map<Identifier, Integer> perLevel;

    /**
     * 单前置构造函数（主/副职业通用）。
     * <p>
     * 传入 {@code prerequisite} 为 null 表示树根；非 null 表示进阶自该基础职业。
     * {@link #getPrerequisites()} 默认据此返回单元素（或空）集合。
     *
     * @param id           职业 ID
     * @param displayName  显示名
     * @param description  描述
     * @param type         主/副职业类型
     * @param prerequisite 前置职业 ID（可为 null）
     * @param maxLevel     等级上限
     * @param baseBonuses  1 级基础加成（attrId → 数值），会被冻结为不可变
     * @param perLevel     每级增量（attrId → 数值），会被冻结为不可变
     */
    protected AbstractProfession(Identifier id, String displayName, String description,
                                 ProfessionType type, @Nullable Identifier prerequisite, int maxLevel,
                                 Map<Identifier, Integer> baseBonuses, Map<Identifier, Integer> perLevel) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.prerequisite = prerequisite;
        this.maxLevel = maxLevel;
        this.baseBonuses = freeze(baseBonuses);
        this.perLevel = freeze(perLevel);
    }

    private static Map<Identifier, Integer> freeze(Map<Identifier, Integer> map) {
        if (map == null || map.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    @Override
    public Identifier getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ProfessionType getType() {
        return type;
    }

    @Override
    public @Nullable Identifier getPrerequisite() {
        return prerequisite;
    }

    /**
     * 默认从 {@link #getPrerequisite()} 派生：单前置包装成单元素集合，null 返回空集。
     * <p>
     * <b>复合职业</b>（多个前置）应直接覆写本方法返回完整前置集合，而非调用本构造。
     */
    @Override
    public Set<Identifier> getPrerequisites() {
        return prerequisite == null ? Set.of() : Set.of(prerequisite);
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public Map<Identifier, Integer> getBaseBonusMap() {
        return baseBonuses;
    }

    @Override
    public int getBonusPerLevel(Identifier attrId) {
        return perLevel.getOrDefault(attrId, 0);
    }

    /** {@inheritDoc} —— 历史别名，等价于 {@link #getBaseBonusMap()} */
    @Override
    public Map<Identifier, Integer> getBonusMap() {
        return baseBonuses;
    }
}
