package com.rpgcraft.core.registry;

import com.rpgcraft.core.attribute.AttackType;
import net.minecraft.resources.Identifier;

/**
 * 攻击类型解析器接口
 * <p>
 * 将武器物品 ID 解析为攻击类型（物理/魔法/混合）。
 * 由装备模块注册实现，供战斗模块查询。
 * <p>
 * 解耦 combat → equipment 的直接依赖：
 * 战斗模块不再直接引用装备模块的 {@code EquipmentManager}，
 * 而是通过 {@link RPGSystems#getAttackTypeResolver()} 获取解析器。
 *
 * @see RPGSystems#registerAttackTypeResolver(IAttackTypeResolver)
 * @see RPGSystems#getAttackTypeResolver()
 */
public interface IAttackTypeResolver {

    /**
     * 根据武器物品 ID 解析攻击类型
     *
     * @param weaponId 武器物品标识符（如 minecraft:diamond_sword）
     * @return 攻击类型，未注册的武器默认返回 {@link AttackType#PHYSICAL}
     */
    AttackType resolve(Identifier weaponId);
}
