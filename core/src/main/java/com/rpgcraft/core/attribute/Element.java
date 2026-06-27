package com.rpgcraft.core.attribute;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * 攻击元素标签枚举
 * <p>
 * 与 {@link AttackType}（物理/魔法/混合）<b>正交</b>，描述攻击的元素归属。
 * 同一次攻击既有伤害类型又有元素标签：例如「火属性物理攻击」「水属性魔法攻击」。
 * <p>
 * <b>元素减伤层</b>：带元素标签（非 NONE）的攻击在基础减伤（物理=防御力减法 /
 * 魔法=法抗百分比 / 混合=两者）<b>之后</b>，额外乘以 {@code (1 - 对应元素抗性/100)}。
 * NONE 元素不触发此层（默认行为零变化）。
 * <p>
 * <b>元素 ↔ 抗性映射</b>：{@link #resistanceId()} 是该映射的唯一真相源，
 * 返回对应抗性属性的 {@link Identifier}（namespace {@code rpgcraftcore}）。
 * 抗性属性由 {@code rpgcraftattributes} 附属模块注册；core 自行声明这些 ID 字面量
 * （遵循「插件互不依赖铁律」，core 不依赖 attributes 模块的 {@code DefaultAttributes}）。
 * <p>
 * <b>元素 ↔ 伤害加成映射</b>：{@link #damageBonusId()} 返回对应「元素伤害加成」属性 ID，
 * 与抗性<b>对称</b>。伤害加成是千分制倍率（1000=基准不变），作用于<b>输出端</b>：
 * 攻击者造成带该元素标签的伤害时，在输出公式计算后、减伤前乘以 {@code bonus/1000}。
 * 默认值 1000（1.0×），故默认行为零变化。抗性作用于<b>受击端</b>（减伤），
 * 二者正交：同一元素可同时「攻击者伤害提升」与「受击者抗性减免」。
 * <p>
 * <b>默认行为</b>：当前所有攻击的元素默认为 {@link #NONE}（{@link com.rpgcraft.core.registry.IElementResolver}
 * 兜底实现返回 NONE）。未来通过实现该 SPI 可启用元素系统。
 */
public enum Element {
    /** 无元素：不触发元素减伤层 */
    NONE,
    /** 电属性 */
    ELECTRIC,
    /** 火属性 */
    FIRE,
    /** 风属性 */
    WIND,
    /** 水属性 */
    WATER,
    /** 光属性 */
    LIGHT,
    /** 毒属性 */
    POISON,
    /** 暗属性 */
    DARK;

    /**
     * 元素的网络/配置名称（小写）
     *
     * @return 如 {@code "none"} / {@code "electric"} / ...
     */
    public String getName() {
        return name().toLowerCase();
    }

    /** 是否为无元素（不触发元素减伤） */
    public boolean isNone() {
        return this == NONE;
    }

    /**
     * 该元素对应的抗性属性 {@link Identifier}
     * <p>
     * 是「元素 ↔ 抗性」映射的唯一真相源。抗性属性由 {@code rpgcraftattributes} 模块注册，
     * 此处声明的 ID 字面量需与该模块保持一致（core 不依赖 attributes 模块）。
     * <p>
     * 抗性作用于<b>受击端</b>：带该元素标签的攻击在基础减伤后额外乘以
     * {@code (1 - 抗性/100)}。
     *
     * @return 对应抗性属性 ID；{@link #NONE} 返回 {@code null}
     */
    @Nullable
    public Identifier resistanceId() {
        return switch (this) {
            case NONE -> null;
            case ELECTRIC -> Identifier.fromNamespaceAndPath("rpgcraftcore", "electric_resistance");
            case FIRE -> Identifier.fromNamespaceAndPath("rpgcraftcore", "fire_resistance");
            case WIND -> Identifier.fromNamespaceAndPath("rpgcraftcore", "wind_resistance");
            case WATER -> Identifier.fromNamespaceAndPath("rpgcraftcore", "water_resistance");
            case LIGHT -> Identifier.fromNamespaceAndPath("rpgcraftcore", "light_resistance");
            case POISON -> Identifier.fromNamespaceAndPath("rpgcraftcore", "poison_resistance");
            case DARK -> Identifier.fromNamespaceAndPath("rpgcraftcore", "dark_resistance");
        };
    }

    /**
     * 该元素对应的「伤害加成」属性 {@link Identifier}
     * <p>
     * 是「元素 ↔ 伤害加成」映射的唯一真相源，与 {@link #resistanceId()} 对称。
     * 伤害加成属性由 {@code rpgcraftattributes} 模块注册；此处声明的 ID 字面量需与
     * 该模块保持一致（core 不依赖 attributes 模块）。
     * <p>
     * 伤害加成作用于<b>输出端</b>：攻击者造成带该元素标签的伤害时，在输出公式计算后、
     * 减伤前乘以 {@code 加成/1000}（千分制倍率，1000 = 基准不变）。
     *
     * @return 对应伤害加成属性 ID；{@link #NONE} 返回 {@code null}
     */
    @Nullable
    public Identifier damageBonusId() {
        return switch (this) {
            case NONE -> null;
            case ELECTRIC -> Identifier.fromNamespaceAndPath("rpgcraftcore", "electric_damage_bonus");
            case FIRE -> Identifier.fromNamespaceAndPath("rpgcraftcore", "fire_damage_bonus");
            case WIND -> Identifier.fromNamespaceAndPath("rpgcraftcore", "wind_damage_bonus");
            case WATER -> Identifier.fromNamespaceAndPath("rpgcraftcore", "water_damage_bonus");
            case LIGHT -> Identifier.fromNamespaceAndPath("rpgcraftcore", "light_damage_bonus");
            case POISON -> Identifier.fromNamespaceAndPath("rpgcraftcore", "poison_damage_bonus");
            case DARK -> Identifier.fromNamespaceAndPath("rpgcraftcore", "dark_damage_bonus");
        };
    }
}
