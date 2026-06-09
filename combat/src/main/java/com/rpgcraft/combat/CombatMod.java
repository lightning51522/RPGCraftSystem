package com.rpgcraft.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.combat.MobRating;
import com.rpgcraft.core.registry.ICombatSystem;
import com.rpgcraft.core.registry.RPGSystems;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * RPG Combat 插件模组入口
 * <p>
 * 提供战斗伤害计算、怪物属性初始化、治疗系统等功能。
 * 仅依赖 RPG Core（rpgcraftcore），不依赖其他插件模组。
 * 通过 Core 的 RPGSystems 接口间接访问装备/等级等数据。
 */
@Mod(CombatMod.MODID)
public class CombatMod {
    public static final String MODID = "rpgcraftcombat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CombatMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Combat 模块初始化");

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
