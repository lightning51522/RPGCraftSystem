package com.rpgcraft.coreattributes;

import com.rpgcraft.combat.CombatEventHandler;
import com.rpgcraft.combat.DefaultDamageCalculator;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attributes.DefaultAttributeModule;
import com.rpgcraft.core.combat.MobRating;
import com.rpgcraft.core.registry.ICombatSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.snapshot.AttributeSnapshotContributor;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Map;

/**
 * 默认属性 + 战斗模块入口
 * <p>
 * 注册全部 13 个 RPG 属性（不含生命，生命由 core 直接注册），
 * 并提供默认战斗系统（伤害公式、怪物属性初始化、治疗）。
 * 可通过第三方模块以 {@link RPGSystems#OVERRIDE_PRIORITY} 覆盖此默认实现。
 */
@Mod(AttributesMod.MODID)
public class AttributesMod {

    public static final String MODID = "rpgcraftattributes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AttributesMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Craft Core Attributes 模块初始化");

        // 注册默认属性模块（默认优先级，可被第三方覆盖）
        RPGSystems.registerAttributeModule(new DefaultAttributeModule(), RPGSystems.DEFAULT_PRIORITY);

        // 注册属性快照贡献者（死亡/重生时的属性保存与恢复）
        SnapshotCoordinator.registerContributor(new AttributeSnapshotContributor());

        // === 战斗系统（合并自原 combat 模块） ===

        // 注册默认伤害计算器（替换 core 的透传兜底）
        AttributeManager.setDamageCalculator(new DefaultDamageCalculator());

        // 注册战斗系统到 RPGSystems 统一门面
        RPGSystems.registerCombatSystem(new ICombatSystem() {
            @Override
            public int healEntity(LivingEntity target, int healAmount, @Nullable LivingEntity healer) {
                return CombatEventHandler.healEntity(target, healAmount, healer);
            }

            @Override
            public void initializeMobAttributes(LivingEntity entity, int targetLevel) {
                CombatEventHandler.initializeMobAttributes(entity, targetLevel);
            }

            @Override
            public void initializeMobAttributesCustom(LivingEntity entity, int targetLevel,
                                                       Map<String, Integer> overrides,
                                                       @Nullable AttackType attackTypeOverride,
                                                       MobRating rating) {
                CombatEventHandler.initializeMobAttributesCustom(entity, targetLevel,
                        overrides, attackTypeOverride, rating);
            }
        });
    }
}
