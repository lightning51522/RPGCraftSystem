package com.rpgcraft.client.ui;

import net.minecraft.resources.Identifier;

/**
 * 客户端模块依赖的游戏属性标识符（本地常量）
 * <p>
 * 这些属性由 {@code rpgcraftattributes} 附属模块注册。本模块不依赖 attributes 模块
 * （遵循插件互不依赖铁律），自行声明所依赖的属性 ID 字面量。
 * 生命属性（LIFE）继续使用 core 的 {@link com.rpgcraft.core.attribute.AttributeManager#LIFE_ID}。
 */
final class ClientAttributes {

    private ClientAttributes() {
    }

    static final Identifier CRITICAL_RATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    static final Identifier CRITICAL_RATIO_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");
    static final Identifier STRENGTH_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    static final Identifier INTELLIGENCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "intelligence");
    static final Identifier AGILE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    static final Identifier PRECISION_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    static final Identifier FIXED_DAMAGE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "fixed_damage");
    static final Identifier RESISTANCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    static final Identifier PHYSICAL_PENETRATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "physical_penetrate");
    static final Identifier MAGICAL_PENETRATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "magical_penetrate");
}
