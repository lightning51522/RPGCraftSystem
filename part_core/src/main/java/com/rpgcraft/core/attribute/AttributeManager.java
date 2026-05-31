package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IDamageCalculator;
import com.rpgcraft.core.attribute.api.IAttributeProvider;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * 属性模块全局门面
 * <p>
 * 保留对 {@link IAttributeRegistry} 和 {@link IDamageCalculator} 的静态引用，
 * 内部委托到 {@link DefaultAttributeRegistry} 和 {@link DefaultDamageCalculator}。
 * <p>
 * 新代码应通过 {@link IAttributeRegistry} 和 {@link IDamageCalculator} 接口访问。
 * <p>
 * 命名与装备模块的 {@link com.rpgcraft.core.equipment.EquipmentManager} 保持一致。
 */
public class AttributeManager {

    /** 内部注册中心实例（保留具体类型供包内直接访问） */
    private static DefaultAttributeRegistry defaultRegistry;

    /** 可替换的伤害计算器 */
    private static IDamageCalculator damageCalculator;

    /**
     * 初始化注册中心和默认战斗计算器
     * <p>
     * 必须在注册 DeferredRegister 到事件总线之前调用。
     * 由 {@link com.rpgcraft.core.RPGCraftCore} 构造函数调用。
     */
    public static void init() {
        defaultRegistry = new DefaultAttributeRegistry("rpgcraftcore");
        damageCalculator = new DefaultDamageCalculator();

        defaultRegistry.register(LIFE_ID, "生命", 100, 100, true, true);
        defaultRegistry.register(SKILL_POINT_ID, "技力", 100, 100, true);
        defaultRegistry.register(MAGIC_POINT_ID, "法力", 100, 100, true);
        defaultRegistry.register(STRENGTH_ID, "力量", 10, Integer.MAX_VALUE);
        defaultRegistry.register(MANA_ID, "魔力", 10, Integer.MAX_VALUE);
        defaultRegistry.register(AGILE_ID, "敏捷", 10, Integer.MAX_VALUE);
        defaultRegistry.register(PRECISION_ID, "精准", 10, Integer.MAX_VALUE);
        defaultRegistry.register(DEFENSE_ID, "防御", 10, Integer.MAX_VALUE);
        defaultRegistry.register(RESISTANCE_ID, "法抗", 2, 100);
        defaultRegistry.register(CRITICAL_RATE_ID, "暴击率", 5, 100);
        defaultRegistry.register(CRITICAL_RATIO_ID, "暴击伤害", 50, Integer.MAX_VALUE);

        // 直接引用注册中心内部的 Supplier，消除每次 .get() 时的 Map 查找和类型转换
        LIFE = defaultRegistry.getRawSupplier(LIFE_ID);
        SKILL_POINT = defaultRegistry.getRawSupplier(SKILL_POINT_ID);
        MAGIC_POINT = defaultRegistry.getRawSupplier(MAGIC_POINT_ID);
        STRENGTH = defaultRegistry.getRawSupplier(STRENGTH_ID);
        MANA = defaultRegistry.getRawSupplier(MANA_ID);
        AGILE = defaultRegistry.getRawSupplier(AGILE_ID);
        PRECISION = defaultRegistry.getRawSupplier(PRECISION_ID);
        DEFENSE = defaultRegistry.getRawSupplier(DEFENSE_ID);
        RESISTANCE = defaultRegistry.getRawSupplier(RESISTANCE_ID);
        CRITICAL_RATE = defaultRegistry.getRawSupplier(CRITICAL_RATE_ID);
        CRITICAL_RATIO = defaultRegistry.getRawSupplier(CRITICAL_RATIO_ID);

        for (IAttributeProvider provider : ServiceLoader.load(IAttributeProvider.class)) {
            provider.registerAttributes(defaultRegistry);
        }
    }

    /**
     * 获取属性注册中心（接口类型）
     * <p>
     * 与 {@link com.rpgcraft.core.equipment.EquipmentManager#getRegistry()} 返回接口类型保持一致。
     */
    public static IAttributeRegistry getRegistry() {
        return defaultRegistry;
    }

    /**
     * 获取底层 DeferredRegister，用于注册到 Mod 事件总线
     * <p>
     * 与 {@link com.rpgcraft.core.equipment.EquipmentData#getAttachmentRegister()} 模式对齐。
     */
    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return defaultRegistry.getDeferredRegister();
    }

    public static IDamageCalculator getDamageCalculator() {
        return damageCalculator;
    }

    public static void setDamageCalculator(IDamageCalculator calculator) {
        damageCalculator = calculator;
    }

    // ====================================================================
    // Identifier 常量
    // ====================================================================

    public static final Identifier LIFE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "life");
    public static final Identifier SKILL_POINT_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");
    public static final Identifier MAGIC_POINT_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "magic_point");
    public static final Identifier STRENGTH_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    public static final Identifier MANA_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "mana");
    public static final Identifier AGILE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    public static final Identifier PRECISION_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    public static final Identifier DEFENSE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense");
    public static final Identifier RESISTANCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    public static final Identifier CRITICAL_RATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    public static final Identifier CRITICAL_RATIO_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");

    // ====================================================================
    // AttachmentType Supplier 访问器（init() 中直接赋值为注册中心的内部引用）
    // ====================================================================

    public static Supplier<AttachmentType<EntityAttribute>> LIFE;
    public static Supplier<AttachmentType<EntityAttribute>> SKILL_POINT;
    public static Supplier<AttachmentType<EntityAttribute>> MAGIC_POINT;
    public static Supplier<AttachmentType<EntityAttribute>> STRENGTH;
    public static Supplier<AttachmentType<EntityAttribute>> MANA;
    public static Supplier<AttachmentType<EntityAttribute>> AGILE;
    public static Supplier<AttachmentType<EntityAttribute>> PRECISION;
    public static Supplier<AttachmentType<EntityAttribute>> DEFENSE;
    public static Supplier<AttachmentType<EntityAttribute>> RESISTANCE;
    public static Supplier<AttachmentType<EntityAttribute>> CRITICAL_RATE;
    public static Supplier<AttachmentType<EntityAttribute>> CRITICAL_RATIO;

    // ====================================================================
    // 原版生命条同步
    // ====================================================================

    /**
     * 将自定义 life 属性同步到原版生命条（按比例缩放）
     * <p>
     * 原版 {@code MAX_HEALTH} 保持默认值 20（10 颗心）不变，
     * 仅按比例设置原版 {@code health}，使心形血条反映自定义生命的百分比。
     * <p>
     * 例如：自定义生命 85/100 → 原版血条 17/20（8.5 颗心），
     * 自定义生命 50/120 → 原版血条 8.33/20（约 4 颗心）。
     * <p>
     * 不负责发送网络同步包——调用方需自行处理客户端同步。
     *
     * @param player 目标玩家
     */
    public static void syncVanillaHealth(ServerPlayer player) {
        EntityAttribute lifeAttr = player.getData(LIFE);
        if (lifeAttr.getMaxValue() <= 0) return; // 避免除零
        // 按比例映射：custom_value / custom_max = vanilla_health / vanilla_max
        float scaledHealth = (float) lifeAttr.getValue() / lifeAttr.getMaxValue() * player.getMaxHealth();
        player.setHealth(Math.min(scaledHealth, player.getMaxHealth()));
    }

    // ====================================================================
    // 查询方法（委托到注册中心）
    // ====================================================================

    public static AttachmentType<EntityAttribute> getTypeById(Identifier id) {
        return defaultRegistry.getRawSupplier(id).get();
    }

    // ====================================================================
    // 伤害计算（委托到伤害计算器）
    // ====================================================================

    public static int getHurt(LivingEntity entity, int originalDamage, AttackType type) {
        return damageCalculator.calculateIncomingDamage(entity, originalDamage, type);
    }

    public static int causeDamage(LivingEntity entity, AttackType type) {
        return damageCalculator.calculateOutgoingDamage(entity, type);
    }
}
