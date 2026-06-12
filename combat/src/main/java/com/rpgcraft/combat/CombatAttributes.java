package com.rpgcraft.combat;

import net.minecraft.resources.Identifier;

/**
 * 战斗模块依赖的游戏属性标识符（本地常量）
 * <p>
 * 这些属性由 {@code rpgcraftattributes} 附属模块注册（生命除外，生命由 core 提供）。
 * 本模块不依赖 attributes 模块（遵循插件互不依赖铁律），而是自行声明所依赖的属性 ID 字面量，
 * 与注册方形成松耦合契约：属性未注册时快照查询返回 0、{@code setIntrinsicBase} 写入无效，
 * 已有优雅降级（伤害不减免但不崩溃）。
 * <p>
 * 生命属性（LIFE）继续使用 {@link com.rpgcraft.core.attribute.AttributeManager#LIFE_ID}（core 提供）。
 */
final class CombatAttributes {

    private CombatAttributes() {
    }

    static final Identifier STRENGTH_ID       = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    static final Identifier MANA_ID           = Identifier.fromNamespaceAndPath("rpgcraftcore", "mana");
    static final Identifier DEFENSE_ID        = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense");
    static final Identifier RESISTANCE_ID     = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    static final Identifier CRITICAL_RATE_ID  = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    static final Identifier CRITICAL_RATIO_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");
    static final Identifier FIXED_DAMAGE_ID   = Identifier.fromNamespaceAndPath("rpgcraftcore", "fixed_damage");
}
