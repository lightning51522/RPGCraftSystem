package com.rpgcraft.core.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.attribute.MobAttributeConfig;
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
 * 仅替换原版的伤害数值计算（攻击力 + 防御减免），其余所有机制保持原版行为：
 * 受击动画、击退、无敌帧、死亡动画、掉落物等均由原版处理。
 * <p>
 * <b>实现策略：</b>
 * <ol>
 *   <li>在 {@link EntityJoinLevelEvent} 中将原版最大生命值同步为自定义 life 值，消除两个血条之间的比例换算</li>
 *   <li>在 {@link LivingDamageEvent.Pre}（原版护甲计算之后、生命值扣除之前）覆盖最终伤害为自定义公式计算值</li>
 *   <li>不取消任何事件，原版伤害流程完整执行</li>
 * </ol>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class CombatEventHandler {

    /**
     * 生物生成/加入世界时初始化自定义属性
     * <p>
     * 将原版最大生命值（{@link Attributes#MAX_HEALTH}）设置为自定义 life 值，
     * 使原版生命条与自定义 life 属性 1:1 对应，无需比例换算。
     * <p>
     * 仅处理非玩家的 LivingEntity，且需要有 JSON 配置。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player) return;

        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<MobAttributeConfig.MobAttributes> config = MobAttributeConfig.getConfig(typeId);
        if (config.isEmpty()) return;

        MobAttributeConfig.MobAttributes attrs = config.get();

        // 将原版最大生命值设置为自定义 life，使两个系统 1:1 同步
        var maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(attrs.life());
        }
        entity.setHealth(attrs.life());

        // 设置自定义属性
        setAttribute(entity, GenericEntityData.LIFE, attrs.life());
        setAttribute(entity, GenericEntityData.STRENGTH, attrs.strength());
        setAttribute(entity, GenericEntityData.DEFENSE, attrs.defense());
        setAttribute(entity, GenericEntityData.RESISTANCE, attrs.resistance());
        setAttribute(entity, GenericEntityData.CRITICAL_RATE, attrs.criticalRate());
        setAttribute(entity, GenericEntityData.CRITICAL_RATIO, attrs.criticalRatio());
    }

    /**
     * 覆盖原版最终伤害为自定义公式计算值
     * <p>
     * {@link LivingDamageEvent.Pre} 在原版护甲/附魔减免之后、生命值扣除之前触发。
     * 我们在此处将伤害值替换为自定义攻防公式的计算结果，从而绕过原版护甲计算，
     * 同时保留原版的所有其他机制（受击动画、击退、无敌帧、死亡处理、掉落物等）。
     * <p>
     * <b>伤害计算流程：</b>
     * <ol>
     *   <li>获取攻击者：若为 LivingEntity，使用 {@link GenericEntityData#causeDamage} 计算攻击力</li>
     *   <li>否则（环境伤害）使用原版原始伤害值</li>
     *   <li>调用 {@link GenericEntityData#getHurt} 计算防御减免后的最终伤害</li>
     *   <li>通过 {@code event.setNewDamage()} 覆盖原版伤害值</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();

        // 获取原版原始伤害（护甲减免前的值）
        float originalDamage = event.getOriginalDamage();
        Entity attackerEntity = event.getSource().getEntity();

        // 计算自定义伤害
        int damage;
        if (attackerEntity instanceof LivingEntity attacker) {
            damage = GenericEntityData.causeDamage(attacker, AttackType.PHYSICAL);
        } else {
            // 环境伤害（摔落、溺水等）直接使用原版值
            damage = (int) Math.ceil(originalDamage);
        }

        // 应用防御减免
        int finalDamage = GenericEntityData.getHurt(target, damage, AttackType.PHYSICAL);

        // 覆盖原版最终伤害值（原版会以此值扣除生命、播放动画、处理死亡）
        event.setNewDamage(finalDamage);
    }

    /**
     * 伤害应用后同步自定义 life 属性与原版生命值
     * <p>
     * 原版已通过 {@code setNewDamage} 扣除了生命值，这里将自定义 life 属性同步为原版当前生命值。
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();

        // 将自定义 life 同步为原版当前生命值
        EntityAttribute lifeAttr = target.getData(GenericEntityData.LIFE);
        int currentVanillaHealth = (int) Math.ceil(target.getHealth());
        lifeAttr.setValue(currentVanillaHealth);

        // 若目标为玩家，同步到客户端更新 HUD
        if (target instanceof ServerPlayer serverPlayer) {
            SyncPlayerAttributePacket.sendToClient(serverPlayer, GenericEntityData.LIFE_ID, lifeAttr);
        }
    }

    /**
     * 设置属性值，同时根据需要更新 maxValue
     * <p>
     * 对于无上限属性（maxValue == MAX_VALUE），仅设置当前值，保持无上限。
     * 对于有上限属性，将 maxValue 和当前值都设置为配置值（满血状态）。
     */
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
