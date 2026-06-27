package com.rpgcraft.core.attribute;

import net.minecraft.resources.Identifier;

/**
 * RPG 属性标识符（{@link Identifier}）的<b>唯一真相源</b>。
 * <p>
 * 本类仅承载<b>稳定的 ID 字符串契约</b>——即「存档 / 网络包 / 数据包配置里指代哪个属性」的
 * 持久化标识。它是<b>纯值类</b>：只有 {@code public static final Identifier} 常量，没有任何方法、
 * 默认值、上限或注册逻辑。
 * <p>
 * <b>命名空间</b>统一为 {@code rpgcraftcore}（与 core 注册中心一致），保持存档/配置向后兼容。
 * 属性「归属」于可移除的附属模块（{@code rpgcraftattributes}），但 ID 稳定不变。
 * <p>
 * <h3>与「可第三方覆盖」的关系（重要）</h3>
 * 第三方覆盖的是<b>属性注册行为</b>（通过 {@code RPGSystems.registerAttributeModule(IAttributeModule,
 * OVERRIDE_PRIORITY)} 替换默认的 {@code DefaultAttributeModule} 实现，即「注册哪些属性、默认值/上限/
 * 是否可加点」），<b>而非</b>这些 ID 常量。本类与覆盖路径完全正交：
 * <ul>
 *   <li>完全替换属性集的第三方会定义自己的新 ID（如 {@code mymod:rage}），不引用本类——零影响。</li>
 *   <li>仅扩展默认集的第三方引用本类常量，反而比各自重声明字面量更安全（消除手抖漂移）。</li>
 * </ul>
 * 因此把 ID 常量集中到本类<b>不会</b>影响属性的可覆盖行为；前提是本类始终只放「字符串契约」，
 * 永不夹带任何注册语义。
 * <p>
 * <h3>默认值 / 上限 / 加点规则在哪？</h3>
 * 这些属于「属性注册语义」，由 {@code rpgcraftattributes} 模块的 {@code DefaultAttributeModule}
 * 决定，可被 OVERRIDE_PRIORITY 覆盖。<b>生命（LIFE）</b>属特例：它需与原版生命/死亡机制对接，
 * 由 core 直接注册（{@link AttributeManager}），任何配置下都必然存在。
 * <p>
 * <b>综合属性（攻击力/防御力）的 ID 仅用于 UI 展示标识</b>——它们不作为真实属性注册，
 * 由伤害公式在计算时根据力量/智力等一般属性动态派生（详见 {@code DefaultDamageCalculator}）。
 *
 * @see Element#resistanceId()
 * @see Element#damageBonusId()
 */
public final class AttributeIds {

    private AttributeIds() {
    }

    // === 生命（特例：由 core 直接注册，与原版生命/死亡对接） ===
    /** 生命值属性 ID（由 core 注册，与原版生命/死亡机制对接） */
    public static final Identifier LIFE_ID               = Identifier.fromNamespaceAndPath("rpgcraftcore", "life");

    // === 资源型属性 ===
    /** 技力（施放技能消耗的资源，重生恢复，有上限） */
    public static final Identifier SKILL_POINT_ID        = Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");

    // === 能力型属性（可加点） ===
    public static final Identifier STRENGTH_ID           = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    public static final Identifier INTELLIGENCE_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");
    public static final Identifier AGILE_ID              = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    public static final Identifier PRECISION_ID          = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    public static final Identifier RESISTANCE_ID         = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    public static final Identifier CRITICAL_RATE_ID      = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    public static final Identifier CRITICAL_RATIO_ID     = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");
    public static final Identifier FIXED_DAMAGE_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "fixed_damage");
    public static final Identifier PHYSICAL_PENETRATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "physical_penetrate");
    public static final Identifier MAGICAL_PENETRATE_ID  = Identifier.fromNamespaceAndPath("rpgcraftcore", "magical_penetrate");
    public static final Identifier EXP_BONUS_ID          = Identifier.fromNamespaceAndPath("rpgcraftcore", "exp_bonus");

    // === 元素抗性型属性（默认 0，上限 100，不可加点，装备加成生效） ===
    /** 电抗性 */
    public static final Identifier ELECTRIC_RESISTANCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "electric_resistance");
    /** 火抗性 */
    public static final Identifier FIRE_RESISTANCE_ID    = Identifier.fromNamespaceAndPath("rpgcraftcore", "fire_resistance");
    /** 风抗性 */
    public static final Identifier WIND_RESISTANCE_ID    = Identifier.fromNamespaceAndPath("rpgcraftcore", "wind_resistance");
    /** 水抗性 */
    public static final Identifier WATER_RESISTANCE_ID   = Identifier.fromNamespaceAndPath("rpgcraftcore", "water_resistance");
    /** 光抗性 */
    public static final Identifier LIGHT_RESISTANCE_ID   = Identifier.fromNamespaceAndPath("rpgcraftcore", "light_resistance");
    /** 毒抗性 */
    public static final Identifier POISON_RESISTANCE_ID  = Identifier.fromNamespaceAndPath("rpgcraftcore", "poison_resistance");
    /** 暗抗性 */
    public static final Identifier DARK_RESISTANCE_ID    = Identifier.fromNamespaceAndPath("rpgcraftcore", "dark_resistance");

    // === 元素伤害加成型属性（默认 1000 = 1.0× 倍率，千分制，作用于输出端，与抗性对称） ===
    /** 电属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier ELECTRIC_DAMAGE_BONUS_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "electric_damage_bonus");
    /** 火属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier FIRE_DAMAGE_BONUS_ID  = Identifier.fromNamespaceAndPath("rpgcraftcore", "fire_damage_bonus");
    /** 风属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier WIND_DAMAGE_BONUS_ID  = Identifier.fromNamespaceAndPath("rpgcraftcore", "wind_damage_bonus");
    /** 水属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier WATER_DAMAGE_BONUS_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "water_damage_bonus");
    /** 光属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier LIGHT_DAMAGE_BONUS_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "light_damage_bonus");
    /** 毒属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier POISON_DAMAGE_BONUS_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "poison_damage_bonus");
    /** 暗属性伤害加成（千分制，1000=基准不变） */
    public static final Identifier DARK_DAMAGE_BONUS_ID  = Identifier.fromNamespaceAndPath("rpgcraftcore", "dark_damage_bonus");

    // === 综合属性 ID（仅用于 UI 展示标识，不注册为真实属性，由伤害公式动态计算） ===
    /** 物理攻击力综合属性 ID（仅用于 UI 标识，伤害公式动态计算） */
    public static final Identifier ATTACK_POWER_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "attack_power");
    /** 防御力综合属性 ID（仅用于 UI 标识，伤害公式动态计算） */
    public static final Identifier DEFENSE_POWER_ID      = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense_power");
}
