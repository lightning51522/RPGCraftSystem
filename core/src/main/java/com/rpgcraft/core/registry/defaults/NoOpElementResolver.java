package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.attribute.Element;
import com.rpgcraft.core.registry.IElementResolver;
import net.minecraft.resources.Identifier;

/**
 * {@link IElementResolver} 的 no-op 兜底实现。
 * <p>
 * 无任何模块注册元素解析器时由 core 预填充：所有武器解析为 {@link Element#NONE}，
 * 兑现 {@link IElementResolver#resolve(Identifier)} Javadoc 已承诺的默认行为
 * （不触发元素减伤层，默认伤害流程零变化）。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getElementResolver()
 */
public final class NoOpElementResolver implements IElementResolver {

    @Override
    public Element resolve(Identifier weaponId) {
        return Element.NONE;
    }
}
