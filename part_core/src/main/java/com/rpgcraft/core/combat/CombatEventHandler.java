package com.rpgcraft.core.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.attribute.api.ICombatCalculator;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.RPGCraftCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.Optional;

/**
 * 自定义战斗系统事件处理器
 * <p>
 * 仅替换原版的伤害数值计算（攻击力 + 防御减免），其余所有机制保持原版行为。
 * 通过 {@link ICombatCalculator} 接口进行伤害计算，可被扩展模组替换。
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class CombatEventHandler {

    /**
     * 生物生成/加入世界时初始化自定义属性
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player) return;

        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<MobAttributeConfig.MobAttributes> config = MobAttributeConfig.getConfig(typeId);
        if (config.isEmpty()) return;

        MobAttributeConfig.MobAttributes attrs = config.get();

        var maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(attrs.life());
        }
        entity.setHealth(attrs.life());

        setAttribute(entity, GenericEntityData.LIFE, attrs.life());
        setAttribute(entity, GenericEntityData.STRENGTH, attrs.strength());
        setAttribute(entity, GenericEntityData.DEFENSE, attrs.defense());
        setAttribute(entity, GenericEntityData.RESISTANCE, attrs.resistance());
        setAttribute(entity, GenericEntityData.CRITICAL_RATE, attrs.criticalRate());
        setAttribute(entity, GenericEntityData.CRITICAL_RATIO, attrs.criticalRatio());
    }

    /**
     * 覆盖原版最终伤害为自定义公式计算值
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        float originalDamage = event.getOriginalDamage();
        Entity attackerEntity = event.getSource().getEntity();

        ICombatCalculator calculator = GenericEntityData.getCombatCalculator();

        int damage;
        if (attackerEntity instanceof LivingEntity attacker) {
            damage = calculator.calculateOutgoingDamage(attacker, AttackType.PHYSICAL);
        } else {
            damage = (int) Math.ceil(originalDamage);
        }

        int finalDamage = calculator.calculateIncomingDamage(target, damage, AttackType.PHYSICAL);
        event.setNewDamage(finalDamage);
    }

    /**
     * 伤害应用后同步自定义 life 属性与原版生命值
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();

        EntityAttribute lifeAttr = target.getData(GenericEntityData.LIFE);
        int currentVanillaHealth = (int) Math.ceil(target.getHealth());
        lifeAttr.setValue(currentVanillaHealth);

        if (target instanceof ServerPlayer serverPlayer) {
            SyncPlayerAttributePacket.sendToClient(serverPlayer, GenericEntityData.LIFE_ID, lifeAttr);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setAttribute(LivingEntity entity,
                                     java.util.function.Supplier<?> supplier,
                                     int value) {
        var typedSupplier = (java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<EntityAttribute>>) supplier;
        EntityAttribute attr = entity.getData(typedSupplier.get());

        if (attr.getMaxValue() != Integer.MAX_VALUE) {
            attr.setMaxValue(value);
        }
        attr.setValue(value);
    }
}
