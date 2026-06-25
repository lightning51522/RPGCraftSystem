package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.combat.MobRating;
import com.rpgcraft.core.registry.ICombatSystem;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * {@link ICombatSystem} 的 no-op 兜底实现。
 * <p>
 * 无战斗模块时由 core 预填充：治疗与怪物属性初始化均为 no-op（不施加任何效果）。
 * 首次调用记录一次 WARN，提示战斗模块未加载。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getCombatSystem()
 */
public final class NoOpCombatSystem implements ICombatSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("RPGCraftCore/NoOpCombatSystem");
    private static volatile boolean warned = false;

    private static void warnOnce() {
        if (!warned) {
            synchronized (NoOpCombatSystem.class) {
                if (!warned) {
                    LOGGER.warn("combat/attributes 模块未加载，ICombatSystem 使用 no-op 兜底（无治疗/怪物初始化）");
                    warned = true;
                }
            }
        }
    }

    @Override
    public int healEntity(LivingEntity target, int healAmount, @Nullable LivingEntity healer) {
        warnOnce();
        return 0; // 无治疗
    }

    @Override
    public void initializeMobAttributes(LivingEntity entity, int targetLevel) {
        warnOnce();
    }

    @Override
    public void initializeMobAttributesCustom(LivingEntity entity, int targetLevel,
                                              Map<String, Integer> overrides,
                                              @Nullable AttackType attackTypeOverride,
                                              MobRating rating) {
        warnOnce();
    }
}
