package com.rpgcraft.client.ui;

import com.rpgcraft.core.attribute.AttributeIds;
import net.minecraft.resources.Identifier;

/**
 * 客户端模块依赖的游戏属性标识符（本地便捷别名）
 * <p>
 * 真相源统一为 core 的 {@link AttributeIds}；本类仅作本模块内的便捷别名引用，
 * 消除字面量重复声明。这些属性由 {@code rpgcraftattributes} 附属模块注册。
 * 生命属性（LIFE）见 {@link com.rpgcraft.core.attribute.AttributeManager#LIFE_ID}。
 */
final class ClientAttributes {

    private ClientAttributes() {
    }

    static final Identifier CRITICAL_RATE_ID      = AttributeIds.CRITICAL_RATE_ID;
    static final Identifier CRITICAL_RATIO_ID     = AttributeIds.CRITICAL_RATIO_ID;
    static final Identifier STRENGTH_ID           = AttributeIds.STRENGTH_ID;
    static final Identifier INTELLIGENCE_ID       = AttributeIds.INTELLIGENCE_ID;
    static final Identifier AGILE_ID              = AttributeIds.AGILE_ID;
    static final Identifier PRECISION_ID          = AttributeIds.PRECISION_ID;
    static final Identifier FIXED_DAMAGE_ID       = AttributeIds.FIXED_DAMAGE_ID;
    static final Identifier RESISTANCE_ID         = AttributeIds.RESISTANCE_ID;
    static final Identifier PHYSICAL_PENETRATE_ID = AttributeIds.PHYSICAL_PENETRATE_ID;
    static final Identifier MAGICAL_PENETRATE_ID  = AttributeIds.MAGICAL_PENETRATE_ID;

    // 元素抗性属性 ID（用于属性抗性面板）
    static final Identifier ELECTRIC_RESISTANCE_ID = AttributeIds.ELECTRIC_RESISTANCE_ID;
    static final Identifier FIRE_RESISTANCE_ID    = AttributeIds.FIRE_RESISTANCE_ID;
    static final Identifier WIND_RESISTANCE_ID    = AttributeIds.WIND_RESISTANCE_ID;
    static final Identifier WATER_RESISTANCE_ID   = AttributeIds.WATER_RESISTANCE_ID;
    static final Identifier LIGHT_RESISTANCE_ID   = AttributeIds.LIGHT_RESISTANCE_ID;
    static final Identifier POISON_RESISTANCE_ID  = AttributeIds.POISON_RESISTANCE_ID;
    static final Identifier DARK_RESISTANCE_ID    = AttributeIds.DARK_RESISTANCE_ID;
}

