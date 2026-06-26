package com.rpgcraft.attributes.module;

import net.minecraft.resources.Identifier;

/**
 * 默认属性词汇表（除生命外的游戏属性标识符）
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
 * <p>
 * <b>综合属性（攻击力/防御力）不在本表</b> —— 它们不作为真实属性注册，由伤害公式
 * 在计算时根据力量/智力等一般属性动态派生（详见 {@code DefaultDamageCalculator}）。
 *
 * <h3>历史变更</h3>
 * <ul>
 *   <li>v0.6.0-alpha：移除 magic_point（资源法力）、mana（能力魔力）、defense（改为综合属性）；
 *       新增 intelligence（智力）</li>
 *   <li>新增元素抗性属性：electric/fire/wind/water/light/poison/dark_resistance（默认 0，上限 100，
 *       不可加点，装备加成生效）；对应攻击元素标签见 {@code Element} 枚举（默认全部 NONE）</li>
 * </ul>
 */
public final class DefaultAttributes {

    private DefaultAttributes() {
    }

    // === 资源型属性 ===
    public static final Identifier SKILL_POINT_ID           = Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");

    // === 能力型属性（可加点） ===
    public static final Identifier STRENGTH_ID              = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    public static final Identifier INTELLIGENCE_ID          = Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");
    public static final Identifier AGILE_ID                 = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    public static final Identifier PRECISION_ID             = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    public static final Identifier RESISTANCE_ID            = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    public static final Identifier CRITICAL_RATE_ID         = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    public static final Identifier CRITICAL_RATIO_ID        = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");
    public static final Identifier FIXED_DAMAGE_ID          = Identifier.fromNamespaceAndPath("rpgcraftcore", "fixed_damage");
    public static final Identifier PHYSICAL_PENETRATE_ID    = Identifier.fromNamespaceAndPath("rpgcraftcore", "physical_penetrate");
    public static final Identifier MAGICAL_PENETRATE_ID     = Identifier.fromNamespaceAndPath("rpgcraftcore", "magical_penetrate");
    public static final Identifier EXP_BONUS_ID             = Identifier.fromNamespaceAndPath("rpgcraftcore", "exp_bonus");

    // === 元素抗性型属性（默认 0，上限 100，不可加点，装备加成生效） ===
    /** 电抗性 */
    public static final Identifier ELECTRIC_RESISTANCE_ID   = Identifier.fromNamespaceAndPath("rpgcraftcore", "electric_resistance");
    /** 火抗性 */
    public static final Identifier FIRE_RESISTANCE_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "fire_resistance");
    /** 风抗性 */
    public static final Identifier WIND_RESISTANCE_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "wind_resistance");
    /** 水抗性 */
    public static final Identifier WATER_RESISTANCE_ID      = Identifier.fromNamespaceAndPath("rpgcraftcore", "water_resistance");
    /** 光抗性 */
    public static final Identifier LIGHT_RESISTANCE_ID      = Identifier.fromNamespaceAndPath("rpgcraftcore", "light_resistance");
    /** 毒抗性 */
    public static final Identifier POISON_RESISTANCE_ID     = Identifier.fromNamespaceAndPath("rpgcraftcore", "poison_resistance");
    /** 暗抗性 */
    public static final Identifier DARK_RESISTANCE_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "dark_resistance");

    // === 综合属性 ID（仅用于 UI 展示标识，不注册为真实属性，由公式动态计算） ===
    /** 物理攻击力综合属性 ID（仅用于 UI 标识，伤害公式动态计算） */
    public static final Identifier ATTACK_POWER_ID          = Identifier.fromNamespaceAndPath("rpgcraftcore", "attack_power");
    /** 防御力综合属性 ID（仅用于 UI 标识，伤害公式动态计算） */
    public static final Identifier DEFENSE_POWER_ID         = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense_power");
}
