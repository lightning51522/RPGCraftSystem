package com.rpgcraft.core.registry;

import com.rpgcraft.core.attribute.Element;
import net.minecraft.resources.Identifier;

/**
 * 攻击元素解析器接口
 * <p>
 * 将武器物品 ID 解析为攻击 {@link Element}（元素标签）。
 * 由装备模块（或其他模块）注册实现，供战斗模块查询。
 * <p>
 * 与 {@link IAttackTypeResolver} 平行：攻击既有「伤害类型」（物理/魔法/混合），
 * 又有「元素标签」（电/火/风/水/光/毒/暗/无），两者正交。
 * <p>
 * 解耦 combat → equipment 的直接依赖：战斗模块通过 {@link RPGSystems#getElementResolver()}
 * 获取解析器，而非直接引用装备模块。
 * <p>
 * <b>默认行为</b>：无任何模块注册实现时，core 预填充 {@link com.rpgcraft.core.registry.defaults.NoOpElementResolver}
 * 兜底，所有武器解析为 {@link Element#NONE}（不触发元素减伤层）。
 * 这与 {@link IAttackTypeResolver} 的 {@code NoOpAttackTypeResolver} 兜底哲学一致。
 *
 * @see RPGSystems#registerElementResolver(IElementResolver)
 * @see RPGSystems#getElementResolver()
 */
public interface IElementResolver {

    /**
     * 根据武器物品 ID 解析攻击元素标签
     *
     * @param weaponId 武器物品标识符（如 minecraft:diamond_sword）
     * @return 攻击元素，未注册的武器默认返回 {@link Element#NONE}
     */
    Element resolve(Identifier weaponId);
}
