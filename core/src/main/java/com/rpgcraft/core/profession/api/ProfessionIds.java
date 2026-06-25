package com.rpgcraft.core.profession.api;

import net.minecraft.resources.Identifier;

/**
 * 职业标识符常量 —— 全工程的职业 ID 单一真相源。
 * <p>
 * 原位于 {@code profession} 插件模块的 {@code ProfessionManager}，但职业内容模块
 * ({@code professions}) 及任何第三方内容包都需要引用这些 ID 来定义/查找职业。
 * 将常量上提到 core 后，{@code professions} 模块不再需要编译期依赖 {@code profession} 插件，
 * 依赖图恢复为严格的星形（所有插件只依赖 core）。
 * <p>
 * 命名空间统一 {@code rpgcraftcore}，与职业 JSON 定义、存档数据保持一致。
 *
 * @see IProfession
 * @see IProfessionRegistry
 */
public final class ProfessionIds {

    private ProfessionIds() {
    }

    /** 平民职业 ID（初始主职业，任何玩家默认拥有）。 */
    public static final Identifier COMMONER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "commoner");

    /** 占位副职业 ID（当 professions 子模块未提供任何 secondary 职业时由子模块注入）。 */
    public static final Identifier APPRENTICE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "apprentice");

    /**
     * 战士职业 ID。
     *
     * @deprecated 具体职业定义已迁移到 {@code professions} 子模块的 Java 类，新代码应通过
     *             {@link IProfessionRegistry#getProfession(Identifier)} 按需查找；
     *             此常量仅保留供系列基类/前置引用。
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier WARRIOR_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "warrior");

    /**
     * 弓箭手职业 ID。
     *
     * @deprecated 见 {@link #WARRIOR_ID}。
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier ARCHER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "archer");

    /**
     * 狂战士职业 ID。
     *
     * @deprecated 见 {@link #WARRIOR_ID}。
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier BERSERKER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "berserker");

    /**
     * 神射手职业 ID。
     *
     * @deprecated 见 {@link #WARRIOR_ID}。
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier MARKSMAN_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "marksman");
}
