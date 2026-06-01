package com.rpgcraft.core.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.AttackType;
import org.jspecify.annotations.Nullable;

/**
 * 怪物等级数据附件
 * <p>
 * 存储怪物的实际等级以及可选的属性覆盖。
 * 通过 {@link com.mojang.serialization.MapCodec} 序列化到实体 NBT，
 * 确保指令召唤的自定义怪物在 chunk 重载后保留所有数据。
 * <p>
 * 默认值为 level=0（表示"未设置"），由 {@link CombatEventHandler#onEntityJoinLevel} 在生成时
 * 设置为配置的默认等级，或由 {@code /rpg spawn} 指令设置为指定等级。
 * <p>
 * 通过 {@code /rpg spawn <entity> <level> {json}} 指令可以额外覆盖 {@code baseExp} 和 {@code attackType}，
 * 这些覆盖值会在击杀经验计算和伤害计算中优先于配置文件。
 * <p>
 * {@code initialized} 标志表示属性是否已完成初始化。从存档加载的实体此标志为 true，
 * 在 {@code EntityJoinLevelEvent} 中跳过重新初始化，从而保留自定义属性值和受伤后的生命值。
 */
public class MobLevelData {

    /** 怪物等级，0 表示未设置 */
    private int level;

    /** 基础经验覆盖，-1 表示未覆盖（使用配置值） */
    private int baseExp;

    /** 攻击类型覆盖，null 表示未覆盖（使用配置值） */
    @Nullable
    private AttackType attackType;

    /** 怪物评级，默认为 NORMAL */
    private MobRating rating;

    /** 属性是否已初始化（true = 已完成，chunk 重载时跳过重新初始化） */
    private boolean initialized;

    public MobLevelData() {
        this.level = 0;
        this.baseExp = -1;
        this.attackType = null;
        this.rating = MobRating.NORMAL;
        this.initialized = false;
    }

    /**
     * 反序列化构造
     */
    private MobLevelData(int level, int baseExp, @Nullable AttackType attackType,
                         MobRating rating, boolean initialized) {
        this.level = level;
        this.baseExp = baseExp;
        this.attackType = attackType;
        this.rating = rating;
        this.initialized = initialized;
    }

    /**
     * 存档序列化 Codec
     * <p>
     * 字段映射：
     * {@code "level"} → 等级,
     * {@code "base_exp"} → 基础经验覆盖,
     * {@code "attack_type"} → 攻击类型（可空）,
     * {@code "rating"} → 评级枚举名,
     * {@code "initialized"} → 属性初始化标志
     */
    public static final MapCodec<MobLevelData> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.fieldOf("level").forGetter(MobLevelData::getLevel),
                    Codec.INT.fieldOf("base_exp").forGetter(MobLevelData::getBaseExpOverride),
                    Codec.STRING.optionalFieldOf("attack_type", null).forGetter(d ->
                            d.hasAttackTypeOverride() ? d.getAttackTypeOverride().name() : null),
                    Codec.STRING.fieldOf("rating").forGetter(d -> d.getRating().name()),
                    Codec.BOOL.fieldOf("initialized").forGetter(MobLevelData::isInitialized)
            ).apply(instance, (level, baseExp, attackTypeName, ratingName, init) -> {
                AttackType at = null;
                if (attackTypeName != null) {
                    try {
                        at = AttackType.valueOf(attackTypeName);
                    } catch (IllegalArgumentException ignored) {
                        // 未知攻击类型，保持 null
                    }
                }
                MobRating r = MobRating.NORMAL;
                try {
                    r = MobRating.valueOf(ratingName);
                } catch (IllegalArgumentException ignored) {
                    // 未知评级，使用默认 NORMAL
                }
                return new MobLevelData(level, baseExp, at, r, init);
            })
    );

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, level);
    }

    /** 是否已设置等级（level > 0） */
    public boolean isSet() {
        return level > 0;
    }

    /**
     * 获取基础经验覆盖值
     *
     * @return 覆盖的经验值，-1 表示未覆盖
     */
    public int getBaseExpOverride() {
        return baseExp;
    }

    /**
     * 设置基础经验覆盖值
     *
     * @param baseExp 经验值，-1 表示清除覆盖
     */
    public void setBaseExpOverride(int baseExp) {
        this.baseExp = baseExp;
    }

    /** 是否设置了基础经验覆盖 */
    public boolean hasBaseExpOverride() {
        return baseExp >= 0;
    }

    /**
     * 获取攻击类型覆盖
     *
     * @return 覆盖的攻击类型，null 表示未覆盖
     */
    @Nullable
    public AttackType getAttackTypeOverride() {
        return attackType;
    }

    /**
     * 设置攻击类型覆盖
     *
     * @param attackType 攻击类型，null 表示清除覆盖
     */
    public void setAttackTypeOverride(@Nullable AttackType attackType) {
        this.attackType = attackType;
    }

    /** 是否设置了攻击类型覆盖 */
    public boolean hasAttackTypeOverride() {
        return attackType != null;
    }

    /**
     * 获取怪物评级
     *
     * @return 评级，默认 {@link MobRating#NORMAL}
     */
    public MobRating getRating() {
        return rating;
    }

    /**
     * 设置怪物评级
     *
     * @param rating 评级枚举
     */
    public void setRating(MobRating rating) {
        this.rating = rating != null ? rating : MobRating.NORMAL;
    }

    /**
     * 属性是否已初始化
     * <p>
     * true 表示属性已通过 {@link CombatEventHandler#initializeMobAttributesCustom} 设置，
     * chunk 重载时应跳过重新初始化以保留自定义数据。
     *
     * @return true = 已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 设置初始化标志
     *
     * @param initialized true = 已初始化
     */
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
