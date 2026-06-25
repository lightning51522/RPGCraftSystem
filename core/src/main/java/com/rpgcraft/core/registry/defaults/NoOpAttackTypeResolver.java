package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.registry.IAttackTypeResolver;
import net.minecraft.resources.Identifier;

/**
 * {@link IAttackTypeResolver} 的 no-op 兜底实现。
 * <p>
 * 无 equipment 模块时由 core 预填充：所有武器解析为 {@link AttackType#PHYSICAL}，
 * 兑现 {@link IAttackTypeResolver#resolve(Identifier)} Javadoc 已承诺的默认行为。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getAttackTypeResolver()
 */
public final class NoOpAttackTypeResolver implements IAttackTypeResolver {

    @Override
    public AttackType resolve(Identifier weaponId) {
        return AttackType.PHYSICAL;
    }
}
