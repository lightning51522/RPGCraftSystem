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
    }
}
