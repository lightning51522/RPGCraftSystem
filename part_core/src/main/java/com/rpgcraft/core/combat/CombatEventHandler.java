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

import java.util.Optional;

/**
 * 自定义战斗系统事件处理器
 * <p>
 * 通过 {@link IDamageCalculator} 接口进行伤害计算，可被扩展模组替换。
 * <p>
 * <h3>伤害分类与处理策略：</h3>
 * <ul>
 *   <li><b>不可减免伤害</b>（虚空、/kill，{@link DamageTypeTags#BYPASSES_INVULNERABILITY}）：
 *       直接透传原版伤害，不做任何修改</li>
 *   <li><b>战斗伤害</b>（有 LivingEntity 攻击者的怪物/玩家攻击）：
 *       使用自定义输出公式计算攻击力，再通过防御力/法抗减免</li>
 *   <li><b>环境伤害</b>（摔落、溺水、火灾、仙人掌、闪电等无攻击者伤害）：
 *       使用原版伤害值直接生效，不应用 RPG 防御力或法抗减免</li>
 * </ul>
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
     *   <li>不可减免伤害（虚空、/kill）→ 直接透传，不调用 {@code setNewDamage()}</li>
     *   <li>战斗伤害（有 LivingEntity 攻击者）→ RPG 攻击力公式 + 防御力减免</li>
     *   <li>环境伤害（摔落、溺水、火灾等）→ 不调用 {@code setNewDamage()}，原版伤害直接生效</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // 1. 不可减免的伤害（虚空、/kill）直接透传，不做任何修改
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        Entity attackerEntity = source.getEntity();
        IDamageCalculator calculator = AttributeManager.getDamageCalculator();

        if (attackerEntity instanceof LivingEntity attacker) {
            // 2. 战斗伤害：使用自定义输出公式 + 防御力减免
            int damage = calculator.calculateOutgoingDamage(attacker, AttackType.PHYSICAL);
            int finalDamage = calculator.calculateIncomingDamage(target, damage, AttackType.PHYSICAL);
            event.setNewDamage(finalDamage);
        }
        // 3. 环境伤害（摔落、溺水、火灾等）：不设置 newDamage，原版伤害值直接生效
    }

    /**
     * 伤害应用后同步自定义 life 属性与原版生命值
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();

        EntityAttribute lifeAttr = target.getData(AttributeManager.LIFE);
        int currentVanillaHealth = (int) target.getHealth() + (target.getHealth() > (int) target.getHealth() ? 1 : 0);
        lifeAttr.setValue(currentVanillaHealth);

        if (target instanceof ServerPlayer serverPlayer) {
            com.rpgcraft.core.RPGCraftCore.checkAndSnapshotIfDying(serverPlayer);
            SyncPlayerAttributePacket.sendToClient(serverPlayer, AttributeManager.LIFE_ID, lifeAttr);
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
