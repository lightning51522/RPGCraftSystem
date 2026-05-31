package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IDamageCalculator;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/**
 * 全局属性注册中心（门面）
 * <p>
 * 保留 Identifier 常量和 Supplier 访问器，内部委托到
 * {@link DefaultAttributeRegistry} 和 {@link DefaultDamageCalculator}。
 * <p>
 * 新代码应通过 {@link com.rpgcraft.core.attribute.api.IAttributeRegistry} 和
 * {@link com.rpgcraft.core.attribute.api.IDamageCalculator} 接口访问。
 */
public class GenericEntityData {

    private static DefaultAttributeRegistry registry;
    private static IDamageCalculator damageCalculator;

    /**
     * 初始化注册中心和默认战斗计算器
     * <p>
     * 必须在注册 DeferredRegister 到事件总线之前调用。
     * 由 {@link com.rpgcraft.core.RPGCraftCore} 构造函数调用。
     */
    public static void init() {
        registry = new DefaultAttributeRegistry("rpgcraftcore");
        damageCalculator = new DefaultDamageCalculator();

        registry.register(LIFE_ID, "生命", 100, 100, true);
        registry.register(SKILL_POINT_ID, "技力", 100, 100, true);
        registry.register(MAGIC_POINT_ID, "法力", 100, 100, true);
        registry.register(STRENGTH_ID, "力量", 10, Integer.MAX_VALUE);
        registry.register(MANA_ID, "魔力", 10, Integer.MAX_VALUE);
        registry.register(AGILE_ID, "敏捷", 10, Integer.MAX_VALUE);
        registry.register(PRECISION_ID, "精准", 10, Integer.MAX_VALUE);
        registry.register(DEFENSE_ID, "防御", 10, Integer.MAX_VALUE);
        registry.register(RESISTANCE_ID, "法抗", 2, 100);
        registry.register(CRITICAL_RATE_ID, "暴击率", 5, 100);
        registry.register(CRITICAL_RATIO_ID, "暴击伤害", 50, Integer.MAX_VALUE);

        // 直接引用注册中心内部的 Supplier，消除每次 .get() 时的 Map 查找和类型转换
        LIFE = registry.getRawSupplier(LIFE_ID);
        SKILL_POINT = registry.getRawSupplier(SKILL_POINT_ID);
        MAGIC_POINT = registry.getRawSupplier(MAGIC_POINT_ID);
        STRENGTH = registry.getRawSupplier(STRENGTH_ID);
        MANA = registry.getRawSupplier(MANA_ID);
        AGILE = registry.getRawSupplier(AGILE_ID);
        PRECISION = registry.getRawSupplier(PRECISION_ID);
        DEFENSE = registry.getRawSupplier(DEFENSE_ID);
        RESISTANCE = registry.getRawSupplier(RESISTANCE_ID);
        CRITICAL_RATE = registry.getRawSupplier(CRITICAL_RATE_ID);
        CRITICAL_RATIO = registry.getRawSupplier(CRITICAL_RATIO_ID);
    }

    public static DefaultAttributeRegistry getRegistry() {
        return registry;
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
    // 查询方法（委托到注册中心）
    // ====================================================================

    public static AttachmentType<EntityAttribute> getTypeById(Identifier id) {
        return registry.getRawSupplier(id).get();
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
