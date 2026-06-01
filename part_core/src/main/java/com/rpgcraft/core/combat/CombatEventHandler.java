package com.rpgcraft.core.combat;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.attribute.api.IDamageCalculator;
import com.rpgcraft.core.command.RPGCommands;
import com.rpgcraft.core.equipment.EquipmentManager;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.combat.RPGDamageEvent;
import com.rpgcraft.core.level.LevelManager;
import com.rpgcraft.core.level.api.IMobAttributeScaler;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.RPGCraftCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * <p>
     * 检查 {@link MobLevelData} 附件确定怪物等级：
     * <ol>
     *   <li>若 {@code initialized=true}（从存档加载），跳过初始化，保留已有属性值</li>
     *   <li>若等级已设置（指令召唤），使用指定等级</li>
     *   <li>若随机刷新开启且有权重配置，从权重表随机选择等级和评级</li>
     *   <li>否则使用配置默认等级</li>
     * </ol>
     * 然后通过 {@link IMobAttributeScaler} 根据等级缩放属性。
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player) return;

        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<MobAttributeConfig.MobAttributes> config = MobAttributeConfig.getConfig(typeId);
        if (config.isEmpty()) return;

        MobLevelData levelData = entity.getData(LevelManager.MOB_LEVEL);

        // 已初始化的实体（从存档加载）：跳过重新初始化，保留所有自定义属性
        if (levelData.isInitialized()) {
            return;
        }

        int level;
        MobRating rating = MobRating.NORMAL;

        if (levelData.isSet()) {
            // 指令预设等级（通常不会命中此分支，指令在 addFreshEntity 之后才设置）
            level = levelData.getLevel();
            rating = levelData.getRating();
        } else if (RPGCommands.isRandomSpawnEnabled()) {
            // 随机刷新开启：从权重表中随机选择等级和评级
            MobAttributeConfig.SpawnDistribution dist = MobAttributeConfig.getSpawnDistribution(typeId);
            if (dist != null) {
                level = weightedRandomLevel(dist.levelWeights(), entity.getRandom());
                rating = weightedRandomRating(dist.ratingWeights(), entity.getRandom());
            } else {
                level = config.get().level();
            }
        } else {
            // 默认：使用配置静态等级
            level = config.get().level();
        }

        initializeMobAttributesCustom(entity, level, Map.of(), null, rating);
    }

    /**
     * 初始化怪物自定义属性，应用等级缩放
     * <p>
     * 此方法为公共 API，可供 {@code /rpg spawn} 指令等外部调用。
     * <ol>
     *   <li>查询 {@link MobAttributeConfig} 获取基础属性值</li>
     *   <li>通过 {@link IMobAttributeScaler} 按等级缩放</li>
     *   <li>设置 vanilla MAX_HEALTH 和自定义属性附件</li>
     *   <li>将等级写入 {@link MobLevelData} 附件</li>
     * </ol>
     *
     * @param entity      目标生物实体
     * @param targetLevel 目标等级（≥ 1）
     */
    public static void initializeMobAttributes(LivingEntity entity, int targetLevel) {
        initializeMobAttributesCustom(entity, targetLevel, Map.of(), null, MobRating.NORMAL);
    }

    /**
     * 初始化怪物自定义属性，支持 JSON 属性覆盖和评级倍率
     * <p>
     * 供 {@code /rpg spawn <entity> <level> {json}} 指令调用。
     * <ul>
     *   <li>{@code overrides} 中的属性值跳过等级缩放直接使用</li>
     *   <li>不在 overrides 中的属性使用配置默认值 + 等级缩放</li>
     *   <li>{@code attackTypeOverride} 非空时覆盖配置的攻击类型</li>
     *   <li>所有属性值最后乘以 {@code rating.getMultiplier()}</li>
     * </ul>
     * 计算流程：配置基础值 → 等级缩放 → JSON 覆盖 → 评级倍率 → 最终值
     *
     * @param entity              目标生物实体
     * @param targetLevel         目标等级（≥ 1）
     * @param overrides           属性覆盖映射（key 为属性名如 "life"，value 为覆盖值）
     * @param attackTypeOverride  攻击类型覆盖，null 表示使用配置值
     * @param rating              怪物评级，决定最终属性倍率
     */
    public static void initializeMobAttributesCustom(LivingEntity entity, int targetLevel,
                                                      Map<String, Integer> overrides,
                                                      AttackType attackTypeOverride,
                                                      MobRating rating) {
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<MobAttributeConfig.MobAttributes> config = MobAttributeConfig.getConfig(typeId);
        if (config.isEmpty()) return;

        MobAttributeConfig.MobAttributes base = config.get();

        // 写入等级到附件
        MobLevelData levelData = entity.getData(LevelManager.MOB_LEVEL);
        levelData.setLevel(targetLevel);

        // 写入评级到附件
        levelData.setRating(rating);

        // 写入攻击类型覆盖
        if (attackTypeOverride != null) {
            levelData.setAttackTypeOverride(attackTypeOverride);
        }

        // 写入 base_exp 覆盖（从 overrides 中提取，不在属性缩放中处理）
        if (overrides.containsKey("base_exp")) {
            levelData.setBaseExpOverride(overrides.get("base_exp"));
        }

        // 获取缩放器
        IMobAttributeScaler scaler = LevelManager.getMobScaler();
        double ratingMult = rating.getMultiplier();

        // 计算每个属性：override 中的值跳过等级缩放，否则从配置缩放
        // 最后统一乘以评级倍率
        int scaledLife = applyRating(overrides.containsKey("life")
                ? overrides.get("life")
                : scaler.scaleAttribute(base.life(), targetLevel, "life"), ratingMult);
        int scaledStrength = applyRating(overrides.containsKey("strength")
                ? overrides.get("strength")
                : scaler.scaleAttribute(base.strength(), targetLevel, "strength"), ratingMult);
        int scaledDefense = applyRating(overrides.containsKey("defense")
                ? overrides.get("defense")
                : scaler.scaleAttribute(base.defense(), targetLevel, "defense"), ratingMult);
        int scaledResistance = applyRating(overrides.containsKey("resistance")
                ? overrides.get("resistance")
                : scaler.scaleAttribute(base.resistance(), targetLevel, "resistance"), ratingMult);
        int scaledCritRate = applyRating(overrides.containsKey("critical_rate")
                ? overrides.get("critical_rate")
                : scaler.scaleAttribute(base.criticalRate(), targetLevel, "critical_rate"), ratingMult);
        int scaledCritRatio = applyRating(overrides.containsKey("critical_ratio")
                ? overrides.get("critical_ratio")
                : scaler.scaleAttribute(base.criticalRatio(), targetLevel, "critical_ratio"), ratingMult);

        // 设置 vanilla 最大生命
        var maxHealthAttr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(scaledLife);
        }
        entity.setHealth(scaledLife);

        // 设置自定义属性
        setAttribute(entity, AttributeManager.LIFE, scaledLife);
        setAttribute(entity, AttributeManager.STRENGTH, scaledStrength);
        setAttribute(entity, AttributeManager.DEFENSE, scaledDefense);
        setAttribute(entity, AttributeManager.RESISTANCE, scaledResistance);
        setAttribute(entity, AttributeManager.CRITICAL_RATE, scaledCritRate);
        setAttribute(entity, AttributeManager.CRITICAL_RATIO, scaledCritRatio);

        // 标记属性已初始化（chunk 重载时跳过重新初始化，保留自定义数据）
        levelData.setInitialized(true);
    }

    /**
     * 应用评级倍率，确保结果 ≥ 1
     */
    private static int applyRating(int value, double multiplier) {
        return Math.max(1, (int) (value * multiplier));
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
     * <p>
     * <h3>RPG 事件集成</h3>
     * <ol>
     *   <li>{@link RPGDamageEvent.Pre} — 伤害计算前，子模块可取消/修改</li>
     *   <li>{@link IDamageCalculator} — 公式计算（策略模式）</li>
     *   <li>应用伤害到 LIFE</li>
     *   <li>{@link RPGDamageEvent.Post} — 伤害应用后，子模块追加效果</li>
     * </ol>
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

        // 确定攻击类型
        AttackType attackType = AttackType.PHYSICAL;
        if (attackerEntity instanceof LivingEntity attacker) {
            if (attacker instanceof Player player) {
                Identifier weaponId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
                attackType = EquipmentManager.getRegistry().getAttackType(weaponId);
            } else {
                MobLevelData attackerLevelData = attacker.getData(LevelManager.MOB_LEVEL);
                if (attackerLevelData.hasAttackTypeOverride()) {
                    attackType = attackerLevelData.getAttackTypeOverride();
                } else {
                    Identifier attackerTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
                    attackType = MobAttributeConfig.getConfig(attackerTypeId)
                            .map(MobAttributeConfig.MobAttributes::attackType)
                            .orElse(AttackType.PHYSICAL);
                }
            }
        }

        // === RPGDamageEvent.Pre：伤害计算前，子模块可取消/修改 ===
        LivingEntity attackerLiving = (attackerEntity instanceof LivingEntity al) ? al : null;
        RPGDamageEvent.Pre preEvent = new RPGDamageEvent.Pre(
                attackerLiving, target, attackType, Math.round(event.getNewDamage()));
        RPGEventBus.post(preEvent);

        if (preEvent.isCancelled()) {
            // 子模块取消了伤害（闪避/无敌等）：将原版伤害设为 0
            event.setNewDamage(0);
            return;
        }

        // 使用子模块可能修改后的攻击类型
        attackType = preEvent.getAttackType();

        int flatDamage;
        if (attackerLiving != null) {
            // 2. 战斗伤害：RPG 公式计算绝对伤害值
            int damage = calculator.calculateOutgoingDamage(attackerLiving, attackType);
            flatDamage = calculator.calculateIncomingDamage(target, damage, attackType);
        } else {
            // 3. 环境伤害：使用 Pre 事件中可能被修改的伤害值
            flatDamage = preEvent.getDamage();
        }

        // 直接扣除自定义生命值
        int newLife = Math.max(0, lifeAttr.getValue() - flatDamage);
        lifeAttr.setValue(newLife);

        // === RPGDamageEvent.Post：伤害应用后，子模块追加效果 ===
        boolean lethal = newLife <= 0;
        RPGDamageEvent.Post postEvent = new RPGDamageEvent.Post(
                attackerLiving, target, flatDamage, attackType, lethal);
        RPGEventBus.post(postEvent);

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

    // === 权重随机选择 ===

    /**
     * 从权重表中按权重随机选择一个等级
     *
     * @param weights 等级 → 权重映射
     * @param random  随机源
     * @return 选中的等级，空表时返回 1
     */
    private static int weightedRandomLevel(Map<Integer, Double> weights, RandomSource random) {
        if (weights.isEmpty()) return 1;
        double total = 0;
        for (double w : weights.values()) {
            total += w;
        }
        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (Map.Entry<Integer, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) return entry.getKey();
        }
        // 兜底：返回第一个键
        return weights.keySet().iterator().next();
    }

    /**
     * 从权重表中按权重随机选择一个评级
     *
     * @param weights 评级枚举名 → 权重映射
     * @param random  随机源
     * @return 选中的评级，空表时返回 NORMAL
     */
    private static MobRating weightedRandomRating(Map<String, Double> weights, RandomSource random) {
        if (weights.isEmpty()) return MobRating.NORMAL;
        double total = 0;
        for (double w : weights.values()) {
            total += w;
        }
        double roll = random.nextDouble() * total;
        double cumulative = 0;
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll < cumulative) {
                try {
                    return MobRating.valueOf(entry.getKey());
                } catch (IllegalArgumentException e) {
                    return MobRating.NORMAL;
                }
            }
        }
        return MobRating.NORMAL;
    }
}
