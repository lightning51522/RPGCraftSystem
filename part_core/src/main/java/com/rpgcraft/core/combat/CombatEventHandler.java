package com.rpgcraft.core.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.attribute.api.IDamageCalculator;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.RPGCraftCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;

import java.util.Optional;

/**
 * 自定义战斗系统事件处理器
 * <p>
 * 通过 {@link IDamageCalculator} 接口进行伤害计算，可被扩展模组替换。
 * <p>
 * <h3>伤害分类与处理策略：</h3>
 * <ul>
 *   <li><b>不可减免伤害</b>（虚空、/kill，{@link DamageTypeTags#BYPASSES_INVULNERABILITY}）：
 *       直接将自定义生命值设为 0，原版伤害透传以触发死亡</li>
 *   <li><b>战斗伤害</b>（有 LivingEntity 攻击者的怪物/玩家攻击）：
 *       使用自定义输出公式计算攻击力（绝对值），直接扣除自定义生命值</li>
 *   <li><b>环境伤害</b>（摔落、溺水、火灾、仙人掌、闪电等无攻击者伤害）：
 *       使用原版伤害值直接扣除自定义生命值（不按比例缩放）</li>
 * </ul>
 * <p>
 * <h3>伤害流程：</h3>
 * <ol>
 *   <li>在 Pre 中计算自定义伤害并直接应用到自定义生命属性</li>
 *   <li>将原版伤害设为对应比例值（使原版生命值同步，确保回血事件正常触发）</li>
 *   <li>在 Post 中校正原版生命值并同步到客户端</li>
 * </ol>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class CombatEventHandler {

    /**
     * 生物生成/加入世界时初始化自定义属性
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
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

        setAttribute(entity, AttributeManager.LIFE, attrs.life());
        setAttribute(entity, AttributeManager.STRENGTH, attrs.strength());
        setAttribute(entity, AttributeManager.DEFENSE, attrs.defense());
        setAttribute(entity, AttributeManager.RESISTANCE, attrs.resistance());
        setAttribute(entity, AttributeManager.CRITICAL_RATE, attrs.criticalRate());
        setAttribute(entity, AttributeManager.CRITICAL_RATIO, attrs.criticalRatio());
    }

    /**
     * 覆盖原版最终伤害为自定义公式计算值
     * <p>
     * 根据伤害来源类型采用不同的处理策略：
     * <ol>
     *   <li>不可减免伤害（虚空、/kill）→ 将自定义生命设为 0，原版伤害透传以触发死亡</li>
     *   <li>战斗伤害（有 LivingEntity 攻击者）→ RPG 攻击力公式（绝对值）直接扣除自定义生命</li>
     *   <li>环境伤害（摔落、溺水、火灾等）→ 原版伤害值直接扣除自定义生命（不按比例缩放）</li>
     * </ol>
     * <p>
     * 自定义生命变更后，将原版伤害设为对应比例值，使原版生命值同步变化。
     * 这样原版回血系统（饱食度、药水等）仍能正常触发 {@code LivingHealEvent}。
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // 1. 不可减免的伤害（虚空、/kill）：直接将自定义生命设为 0，原版伤害透传
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            EntityAttribute lifeAttr = target.getData(AttributeManager.LIFE);
            lifeAttr.setValue(0);
            return;
        }

        Entity attackerEntity = source.getEntity();
        IDamageCalculator calculator = AttributeManager.getDamageCalculator();
        EntityAttribute lifeAttr = target.getData(AttributeManager.LIFE);

        int flatDamage;
        if (attackerEntity instanceof LivingEntity attacker) {
            // 2. 战斗伤害：RPG 公式计算绝对伤害值
            // 从配置获取攻击者的攻击类型，未配置时默认为 PHYSICAL
            Identifier attackerTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
            AttackType attackType = MobAttributeConfig.getConfig(attackerTypeId)
                    .map(MobAttributeConfig.MobAttributes::attackType)
                    .orElse(AttackType.PHYSICAL);
            int damage = calculator.calculateOutgoingDamage(attacker, attackType);
            flatDamage = calculator.calculateIncomingDamage(target, damage, attackType);
        } else {
            // 3. 环境伤害：原版伤害值直接作为自定义生命扣减量（不按比例缩放）
            flatDamage = Math.round(event.getNewDamage());
        }

        // 直接扣除自定义生命值
        int newLife = Math.max(0, lifeAttr.getValue() - flatDamage);
        lifeAttr.setValue(newLife);

        // 将原版伤害设为对应比例值，使原版生命条同步变化
        if (newLife <= 0) {
            // 自定义生命耗尽：设为致命伤害确保原版死亡
            event.setNewDamage(target.getHealth() + target.getAbsorptionAmount() + 1);
        } else if (lifeAttr.getMaxValue() > 0) {
            // 计算与自定义生命比例对应的原版伤害量
            float newVanillaHealth = (float) newLife / lifeAttr.getMaxValue() * target.getMaxHealth();
            event.setNewDamage(Math.max(0, target.getHealth() - newVanillaHealth));
        }
        // maxValue <= 0 时不设置原版伤害，避免除零错误；原版伤害保持原值
    }

    /**
     * 伤害应用后校正原版生命值并同步到客户端
     * <p>
     * 自定义生命值已在 {@link #onLivingDamagePre} 中更新，此处不再做比例转换。
     * 仅校正原版生命值使其与自定义生命比例一致（确保原版回血系统正常工作），
     * 并检查死亡快照、发送客户端同步包。
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();

        EntityAttribute lifeAttr = target.getData(AttributeManager.LIFE);

        // 校正原版生命值：使其与自定义生命比例一致
        // （处理吸收等原版机制可能导致的偏差）
        if (lifeAttr.getValue() > 0 && lifeAttr.getMaxValue() > 0) {
            float expected = (float) lifeAttr.getValue() / lifeAttr.getMaxValue() * target.getMaxHealth();
            if (Math.abs(target.getHealth() - expected) > 0.5f) {
                target.setHealth(expected);
            }
        }

        if (target instanceof ServerPlayer serverPlayer) {
            com.rpgcraft.core.RPGCraftCore.checkAndSnapshotIfDying(serverPlayer);
            SyncPlayerAttributePacket.sendToClient(serverPlayer, AttributeManager.LIFE_ID, lifeAttr);
        }
    }

    /**
     * 回血事件处理 —— 将原版回血同步到自定义 life 属性
     * <p>
     * 原版回血来源包括：饱食度自然回复、再生药水、信标效果、金苹果、治疗药水等。
     * 所有通过 {@code LivingEntity.heal()} 触发的回血都会被此事件捕获。
     * <p>
     * 不会与 {@link AttributeManager#syncVanillaHealth} 形成循环：
     * {@code syncVanillaHealth} 使用 {@code setHealth()} 而非 {@code heal()}，
     * 因此不触发此事件。
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        float healAmount = event.getAmount();
        EntityAttribute lifeAttr = serverPlayer.getData(AttributeManager.LIFE);

        // 防御性检查：原版最大生命值为 0 时跳过比例转换（极罕见，可能由其他模组导致）
        if (serverPlayer.getMaxHealth() <= 0) return;

        // 将原版回血量按比例转换为自定义生命值
        int customHeal = Math.round(healAmount * lifeAttr.getMaxValue() / serverPlayer.getMaxHealth());
        lifeAttr.setValue(lifeAttr.getValue() + customHeal);

        SyncPlayerAttributePacket.sendToClient(serverPlayer, AttributeManager.LIFE_ID, lifeAttr);
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
