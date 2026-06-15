package com.rpgcraft.coreattributes;

import com.rpgcraft.combat.CombatEventHandler;
import com.rpgcraft.combat.DefaultDamageCalculator;
import com.rpgcraft.combat.RandomSpawnSavedData;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attributes.DefaultAttributeModule;
import com.rpgcraft.core.combat.MobRating;
import com.rpgcraft.core.registry.ICombatSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.snapshot.AttributeSnapshotContributor;
import com.rpgcraft.core.snapshot.SnapshotCoordinator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
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

        // 注册全局事件监听（服务端启动时预加载随机刷新开关的内存镜像）
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * 主世界首次加载时预加载 {@link RandomSpawnSavedData}
     * <p>
     * 确保随机刷新开关的内存镜像在任何怪物自然生成之前从存档恢复，
     * 避免 {@link RandomSpawnSavedData#isEnabled()} 在首只怪物入世界时仍为默认值。
     * <p>
     * SavedData 挂载在 {@link net.minecraft.server.MinecraftServer#getDataStorage()} 上
     * （服务器全局，不绑定维度），这里借用主世界加载事件作为预加载时机（主世界总是首个加载）。
     *
     * @param event 世界加载事件
     */
    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel && serverLevel.equals(serverLevel.getServer().overworld())) {
            // 触发 SavedData 加载，同步内存镜像
            RandomSpawnSavedData.get(serverLevel.getServer());
        }
    }
}
