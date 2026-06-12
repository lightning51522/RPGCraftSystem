package com.rpgcraft.core.attributes;

import com.rpgcraft.core.attribute.DefaultAttributeRegistry;
import com.rpgcraft.core.attribute.api.IAttributeModule;

/**
 * 默认属性模块实现
 * <p>
 * 注册除生命外的 11 个 RPG 核心游戏属性：
 * <ul>
 *   <li>资源型：技力、法力（重生恢复，有上限）</li>
 *   <li>能力型：力量、魔力、敏捷、精准、防御、法抗、暴击率、暴击伤害、固定伤害</li>
 * </ul>
 * <p>
 * <b>生命属性（LIFE）不在此注册</b> —— 它由 core 直接提供（与原版生命/死亡机制对接），
 * 任何配置下必然存在。本模块仅定义可替换的游戏属性。
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
        registry.register(DefaultAttributes.SKILL_POINT_ID, "技力", "施放物理类技能所消耗的资源，重生时恢复。",
                100, 100, true, false);
        registry.register(DefaultAttributes.MAGIC_POINT_ID, "法力", "施放法术所消耗的资源，重生时恢复。",
                100, 100, true, false);

        // === 能力型属性（无上限或独立上限） ===
        registry.register(DefaultAttributes.STRENGTH_ID, "力量", "提高物理攻击造成的伤害。",
                10, Integer.MAX_VALUE, false, false);
        registry.register(DefaultAttributes.MANA_ID, "魔力", "提高法术攻击造成的伤害。",
                10, Integer.MAX_VALUE, false, false);
        registry.register(DefaultAttributes.AGILE_ID, "敏捷", "影响行动速度与闪避表现。",
                10, Integer.MAX_VALUE, false, false);
        registry.register(DefaultAttributes.PRECISION_ID, "精准", "提高攻击的命中与稳定性。",
                10, Integer.MAX_VALUE, false, false);
        registry.register(DefaultAttributes.DEFENSE_ID, "防御", "减免受到的物理伤害。",
                10, Integer.MAX_VALUE, false, false);
        registry.register(DefaultAttributes.RESISTANCE_ID, "法抗", "减免受到的法术伤害。",
                2, 100, false, false);
        registry.register(DefaultAttributes.CRITICAL_RATE_ID, "暴击率", "攻击触发暴击的概率（数值为百分比）。",
                5, 300, false, false);
        registry.register(DefaultAttributes.CRITICAL_RATIO_ID, "暴击伤害", "暴击时额外提升的伤害倍率。",
                50, Integer.MAX_VALUE, false, false);
        registry.register(DefaultAttributes.FIXED_DAMAGE_ID, "固定伤害", "每次攻击额外附加的固定伤害。",
                0, Integer.MAX_VALUE, false, false);
    }
}
