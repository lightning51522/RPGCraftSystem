package com.rpgcraft.attributes.module;

import net.minecraft.resources.Identifier;

/**
 * 默认属性词汇表（除生命外的 13 个游戏属性标识符）
 * <p>
 * 本类是默认游戏属性集的<b>唯一真相源</b>。生命属性（LIFE）由 core 直接提供
 * （{@code com.rpgcraft.core.attribute.AttributeManager#LIFE_ID}），不在此处，
 * 因为它需要与原版生命/死亡机制对接、任何配置下都必然存在。
 * <p>
 * 命名空间统一为 {@code rpgcraftcore}（与 core 注册中心一致），保持存档/配置向后兼容。
 * 属性「归属」于本可移除附属模块，但 ID 稳定不变。
 * <p>
 * 消费这些属性的默认规则集模块（combat/profession/client）各自声明本地常量引用同一字面量，
 * 形成松耦合契约，不依赖本类（遵循插件互不依赖铁律）。
 */
public final class DefaultAttributes {

    private DefaultAttributes() {
    }

    public static final Identifier SKILL_POINT_ID           = Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");
    public static final Identifier MAGIC_POINT_ID           = Identifier.fromNamespaceAndPath("rpgcraftcore", "magic_point");
    public static final Identifier STRENGTH_ID              = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    public static final Identifier MANA_ID                  = Identifier.fromNamespaceAndPath("rpgcraftcore", "mana");
    public static final Identifier AGILE_ID                 = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    public static final Identifier PRECISION_ID             = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    public static final Identifier DEFENSE_ID               = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense");
    public static final Identifier RESISTANCE_ID            = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    public static final Identifier CRITICAL_RATE_ID         = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    public static final Identifier CRITICAL_RATIO_ID        = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");
    public static final Identifier FIXED_DAMAGE_ID          = Identifier.fromNamespaceAndPath("rpgcraftcore", "fixed_damage");
    public static final Identifier PHYSICAL_PENETRATE_ID    = Identifier.fromNamespaceAndPath("rpgcraftcore", "physical_penetrate");
    public static final Identifier MAGICAL_PENETRATE_ID     = Identifier.fromNamespaceAndPath("rpgcraftcore", "magical_penetrate");
}
