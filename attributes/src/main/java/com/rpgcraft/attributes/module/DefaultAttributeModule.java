package com.rpgcraft.attributes.module;

import com.rpgcraft.core.attribute.DefaultAttributeRegistry;
import com.rpgcraft.core.attribute.api.IAttributeModule;

/**
 * 默认属性模块实现
 * <p>
 * 注册除生命外的 RPG 核心游戏属性：
 * <ul>
 *   <li>资源型：技力（重生恢复，有上限）。所有技能（物理/魔法）统一消耗技力。</li>
 *   <li>能力型：力量、智力、敏捷、精准、法抗、暴击率、暴击伤害、固定伤害、物理穿透、法术穿透</li>
 *   <li>元素抗性型：电抗/火抗/风抗/水抗/光抗/毒抗/暗抗（默认 0，上限 100，不可加点，
 *       装备加成生效；减免对应元素标签攻击的伤害）</li>
 *   <li>元素伤害加成型：电/火/风/水/光/毒/暗伤害加成（默认 1000 = 1.0× 倍率，千分制，
 *       作用于输出端；攻击者造成带元素标签伤害时，输出公式后乘以 加成/1000）</li>
 * </ul>
 * <p>
 * <b>生命属性（LIFE）不在此注册</b> —— 它由 core 直接提供（与原版生命/死亡机制对接），
 * 任何配置下必然存在。本模块仅定义可替换的游戏属性。
 * <p>
 * <b>综合属性（攻击力/防御力）不在此注册</b> —— 它们由伤害公式根据一般属性动态计算：
 * <ul>
 *   <li>物理攻击力 = 力量×2 + 智力 + 装备加成</li>
 *   <li>魔法攻击力 = 智力×2 + 力量 + 装备加成</li>
 *   <li>物理防御力 = 力量×2 + 装备加成（魔法防御力仅来自装备）</li>
 * </ul>
 * <p>
 * 第三方可通过实现 {@link IAttributeModule} 并以更高优先级注册来替换此默认实现，
 * 定义一套完全不同的游戏属性集。
 *
 * @see IAttributeModule
 * @see DefaultAttributes
 */
public class DefaultAttributeModule implements IAttributeModule {

    @Override
    public void registerAttributes(DefaultAttributeRegistry registry) {
        // === 资源型属性（有上限，重生恢复） ===
        registry.register(DefaultAttributes.SKILL_POINT_ID, "技力", "施放技能所消耗的资源（物理/魔法技能统一消耗），重生时恢复。",
                100, 100, true, false);

        // === 能力型属性（无上限或独立上限） ===
        registry.register(DefaultAttributes.STRENGTH_ID, "力量", "提高物理攻击力与物理防御力，并少量提升魔法攻击力。",
                10, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.INTELLIGENCE_ID, "智力", "提高魔法攻击力，并少量提升物理攻击力。",
                10, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.AGILE_ID, "敏捷", "每 5 点增加 1 点暴击率。",
                10, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.PRECISION_ID, "精准", "每 5 点增加 2 点暴击伤害。",
                10, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.RESISTANCE_ID, "法抗", "减免受到的法术伤害（百分比）。",
                0, 100, false, false);

        registry.register(DefaultAttributes.CRITICAL_RATE_ID, "暴击率", "攻击触发暴击的百分比概率，另受敏捷派生（每 5 敏捷 +1）。不可加点，装备加成生效。",
                5, 300, false, false, false, null);

        registry.register(DefaultAttributes.CRITICAL_RATIO_ID, "暴击伤害", "暴击时额外提升的伤害百分比倍率，另受精准派生（每 5 精准 +2）。不可加点，装备加成生效。",
                50, Integer.MAX_VALUE, false, false, false, null);

        registry.register(DefaultAttributes.FIXED_DAMAGE_ID, "固定伤害", "每次攻击额外附加的固定伤害。",
                0, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.PHYSICAL_PENETRATE_ID, "物理穿透", "攻击时无视目标物理防御的值。",
                0, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.MAGICAL_PENETRATE_ID, "法术穿透", "攻击时无视目标法抗的值。",
                0, Integer.MAX_VALUE, false, false);

        registry.register(DefaultAttributes.EXP_BONUS_ID, "经验加成", "按百分比提升击杀获得的经验（叠加在等级差曲线之上）。不可加点，装备/职业加成生效。",
                0, Integer.MAX_VALUE, false, false, false, null);

        // === 元素抗性型属性（默认 0，上限 100，不可加点，装备加成生效） ===
        // 减免对应元素标签攻击的伤害：基础减伤后额外乘以 (1 - 抗性/100)。
        // 当前所有攻击元素默认为 NONE（不触发元素减伤层），故默认行为零变化。
        // 未来通过实现 IElementResolver 可为武器/怪物配置元素标签启用此机制。
        registry.register(DefaultAttributes.ELECTRIC_RESISTANCE_ID, "电抗", "减免受到的电属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);
        registry.register(DefaultAttributes.FIRE_RESISTANCE_ID, "火抗", "减免受到的火属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);
        registry.register(DefaultAttributes.WIND_RESISTANCE_ID, "风抗", "减免受到的风属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);
        registry.register(DefaultAttributes.WATER_RESISTANCE_ID, "水抗", "减免受到的水属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);
        registry.register(DefaultAttributes.LIGHT_RESISTANCE_ID, "光抗", "减免受到的光属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);
        registry.register(DefaultAttributes.POISON_RESISTANCE_ID, "毒抗", "减免受到的毒属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);
        registry.register(DefaultAttributes.DARK_RESISTANCE_ID, "暗抗", "减免受到的暗属性攻击伤害（百分比）。不可加点，装备加成生效。",
                0, 100, false, false, false, null);

        // === 元素伤害加成型属性（默认 1000 = 1.0× 倍率，千分制，不可加点，装备/区域加成生效） ===
        // 作用于输出端：攻击者造成带元素标签伤害时，输出公式计算后乘以 加成/1000。
        // 与元素抗性对称（抗性作用于受击端减伤，加成作用于输出端增伤）。
        // 当前所有攻击元素默认为 NONE（不触发输出倍率），故默认行为零变化。
        // 未来通过实现 IElementResolver 或区域系统（rpgcraftregion）可启用此机制。
        registry.register(DefaultAttributes.ELECTRIC_DAMAGE_BONUS_ID, "电属性伤害加成", "提升造成的电属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
        registry.register(DefaultAttributes.FIRE_DAMAGE_BONUS_ID, "火属性伤害加成", "提升造成的火属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
        registry.register(DefaultAttributes.WIND_DAMAGE_BONUS_ID, "风属性伤害加成", "提升造成的风属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
        registry.register(DefaultAttributes.WATER_DAMAGE_BONUS_ID, "水属性伤害加成", "提升造成的水属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
        registry.register(DefaultAttributes.LIGHT_DAMAGE_BONUS_ID, "光属性伤害加成", "提升造成的光属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
        registry.register(DefaultAttributes.POISON_DAMAGE_BONUS_ID, "毒属性伤害加成", "提升造成的毒属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
        registry.register(DefaultAttributes.DARK_DAMAGE_BONUS_ID, "暗属性伤害加成", "提升造成的暗属性伤害（千分制，1000=基准不变）。不可加点，装备/区域加成生效。",
                1000, Integer.MAX_VALUE, false, false, false, null);
    }
}
