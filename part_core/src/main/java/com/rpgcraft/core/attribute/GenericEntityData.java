package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.ICombatCalculator;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/**
 * 全局属性注册中心（门面）
 * <p>
 * 保留 Identifier 常量和 Supplier 访问器，内部委托到
 * {@link DefaultAttributeRegistry} 和 {@link DefaultCombatCalculator}。
 * <p>
 * 新代码应通过 {@link com.rpgcraft.core.attribute.api.IAttributeRegistry} 和
 * {@link com.rpgcraft.core.attribute.api.ICombatCalculator} 接口访问。
 */
public class GenericEntityData {

    private static DefaultAttributeRegistry registry;
    private static ICombatCalculator combatCalculator;

    /**
     * 初始化注册中心和默认战斗计算器
     * <p>
     * 必须在注册 DeferredRegister 到事件总线之前调用。
     * 由 {@link com.rpgcraft.core.RPGCraftCore} 构造函数调用。
     */
    public static void init() {
        registry = new DefaultAttributeRegistry("rpgcraftcore");
        combatCalculator = new DefaultCombatCalculator();

        registry.register(LIFE_ID, "生命", 100, 100);
        registry.register(SKILL_POINT_ID, "技力", 100, 100);
        registry.register(MAGIC_POINT_ID, "法力", 100, 100);
        registry.register(STRENGTH_ID, "力量", 10, Integer.MAX_VALUE);
        registry.register(MANA_ID, "魔力", 10, Integer.MAX_VALUE);
        registry.register(AGILE_ID, "敏捷", 10, Integer.MAX_VALUE);
        registry.register(PRECISION_ID, "精准", 10, Integer.MAX_VALUE);
        registry.register(DEFENSE_ID, "防御", 10, Integer.MAX_VALUE);
        registry.register(RESISTANCE_ID, "法抗", 2, 100);
        registry.register(CRITICAL_RATE_ID, "暴击率", 5, 100);
        registry.register(CRITICAL_RATIO_ID, "暴击伤害", 50, Integer.MAX_VALUE);
    }

    public static DefaultAttributeRegistry getRegistry() {
        return registry;
    }

    public static ICombatCalculator getCombatCalculator() {
        return combatCalculator;
    }

    public static void setCombatCalculator(ICombatCalculator calculator) {
        combatCalculator = calculator;
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
    // AttachmentType Supplier 访问器（委托到注册中心）
    // ====================================================================

    @SuppressWarnings("unchecked")
    private static Supplier<AttachmentType<EntityAttribute>> delegate(Identifier id) {
        return () -> (AttachmentType<EntityAttribute>) (AttachmentType<?>) registry.getTypeById(id);
    }

    public static final Supplier<AttachmentType<EntityAttribute>> LIFE = delegate(LIFE_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> SKILL_POINT = delegate(SKILL_POINT_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> MAGIC_POINT = delegate(MAGIC_POINT_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> STRENGTH = delegate(STRENGTH_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> MANA = delegate(MANA_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> AGILE = delegate(AGILE_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> PRECISION = delegate(PRECISION_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> DEFENSE = delegate(DEFENSE_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> RESISTANCE = delegate(RESISTANCE_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> CRITICAL_RATE = delegate(CRITICAL_RATE_ID);
    public static final Supplier<AttachmentType<EntityAttribute>> CRITICAL_RATIO = delegate(CRITICAL_RATIO_ID);

    // ====================================================================
    // 查询方法（委托到注册中心）
    // ====================================================================

    @SuppressWarnings("unchecked")
    public static AttachmentType<EntityAttribute> getTypeById(Identifier id) {
        return (AttachmentType<EntityAttribute>) (AttachmentType<?>) registry.getTypeById(id);
    }

    // ====================================================================
    // 伤害计算（委托到战斗计算器）
    // ====================================================================

    public static int getHurt(LivingEntity entity, int originalDamage, AttackType type) {
        return combatCalculator.calculateIncomingDamage(entity, originalDamage, type);
    }

    public static int causeDamage(LivingEntity entity, AttackType type) {
        return combatCalculator.calculateOutgoingDamage(entity, type);
    }
}
