package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 由 datapack JSON 定义的职业实现。
 * <p>
 * 所有字段在构造时从 JSON 解析得到，运行时不可变。取代了旧版本中每个内置职业一个 Java 类
 * （{@code WarriorProfession} 等）的硬编码做法 —— 框架与具体职业彻底解耦，新增/修改职业只需
 * 增改 {@code data/rpgcraftcore/rpg/professions/*.json}，无需改动 Java 代码。
 * <p>
 * 由 {@link ProfessionDefinitionLoader} 解析 JSON 后构造并注册到 {@link ProfessionRegistry}。
 *
 * @see IProfession
 * @see ProfessionDefinitionLoader
 */
public final class JsonProfession implements IProfession {

    private final Identifier id;
    private final String displayName;
    private final String description;
    private final ProfessionType type;
    private final @Nullable Identifier prerequisite;
    private final int maxLevel;
    private final Map<Identifier, Integer> baseBonuses;
    private final Map<Identifier, Integer> perLevel;
    private final int @Nullable [] expTable;

    /**
     * @param id           职业 ID
     * @param displayName  显示名
     * @param description  描述
     * @param type         主/副职业类型
     * @param prerequisite 前置职业 ID（可为 null）
     * @param maxLevel     等级上限
     * @param baseBonuses  1 级基础加成（attrId → 数值），会被冻结为不可变
     * @param perLevel     每级增量（attrId → 数值），会被冻结为不可变
     * @param expTable     专属经验表（可为 null 表示用全局公式）
     */
    public JsonProfession(Identifier id, String displayName, String description,
                          ProfessionType type, @Nullable Identifier prerequisite, int maxLevel,
                          Map<Identifier, Integer> baseBonuses, Map<Identifier, Integer> perLevel,
                          int @Nullable [] expTable) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.prerequisite = prerequisite;
        this.maxLevel = maxLevel;
        this.baseBonuses = Collections.unmodifiableMap(new LinkedHashMap<>(baseBonuses));
        this.perLevel = Collections.unmodifiableMap(new LinkedHashMap<>(perLevel));
        this.expTable = expTable == null ? null : expTable.clone();
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

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public int @Nullable [] getExpTable() {
        return expTable == null ? null : expTable.clone();
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
