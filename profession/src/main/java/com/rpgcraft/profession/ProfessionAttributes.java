package com.rpgcraft.profession;

import net.minecraft.resources.Identifier;

/**
 * 职业模块依赖的游戏属性标识符（本地常量）
 * <p>
 * 这些属性由 {@code rpgcraftattributes} 附属模块注册。本模块不依赖 attributes 模块
 * （遵循插件互不依赖铁律），而是自行声明所依赖的属性 ID 字面量，与注册方形成松耦合契约：
 * 属性未注册时加成落到不存在的属性上、不生效但不崩溃。
 */
final class ProfessionAttributes {

    private ProfessionAttributes() {
    }

    static final Identifier STRENGTH_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    static final Identifier AGILE_ID    = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    /** 生命属性（狂战士使用）。属性未注册时加成静默失效，不崩溃 */
    static final Identifier LIFE_ID     = Identifier.fromNamespaceAndPath("rpgcraftcore", "life");
    /** 暴击属性（神射手使用） */
    static final Identifier CRITICAL_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
}
