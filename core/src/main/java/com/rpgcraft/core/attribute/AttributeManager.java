package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IDamageCalculator;
import com.rpgcraft.core.attribute.api.IAttributeModule;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import com.rpgcraft.core.preference.PlayerPreferences;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Supplier;

/**
 * 属性模块全局门面
 * <p>
 * 保留对 {@link IAttributeRegistry} 和 {@link IDamageCalculator} 的静态引用，
 * 内部委托到 {@link DefaultAttributeRegistry}。
 * 伤害计算器默认为透传实现，由 combat 模块通过
 * {@link #setDamageCalculator(IDamageCalculator)} 注册真实实现。
 * <p>
 * <b>生命属性（LIFE）由 core 直接定义并注册</b>。LIFE 需要与原版生命/死亡机制
 * （原版血条同步、死亡快照触发、伤害扣血/回血）对接，属于核心机制而非游戏设计，
 * 因此任何配置下都必然存在，第三方属性附属无需（也不应）重新提供。
 * <p>
 * 其余游戏属性（力量、防御、暴击等）由 {@code rpgcraftattributes} 附属模块通过
 * {@link IAttributeModule} 提供，第三方模组可通过
 * {@link RPGSystems#registerAttributeModule(IAttributeModule, int)} 完全替换。
 * core 不硬编码任何这些游戏属性。
 * <p>
 * 命名与装备模块的 {@code EquipmentManager} 保持一致。
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
     * <p>
     * 默认使用透传计算器（不减免、不暴击），由 combat 模块通过
     * {@link #setDamageCalculator(IDamageCalculator)} 注册真实的伤害公式。
     * <p>
     * 此处注册 core 自有的生命属性（LIFE）；其余游戏属性由通过
     * {@link RPGSystems#registerAttributeModule} 注册的 {@link IAttributeModule}
     * 在 {@link #onRegisterAttachmentTypes(RegisterEvent)} 中注册。
     */
    public static void init() {
        defaultRegistry = new DefaultAttributeRegistry("rpgcraftcore");
        // 透传计算器：无战斗模块时的兜底，直接返回原始伤害
        damageCalculator = new IDamageCalculator() {
            @Override
            public int calculateIncomingDamage(LivingEntity target, int originalDamage, AttackType type) {
                return originalDamage;
            }
            @Override
            public int calculateOutgoingDamage(LivingEntity attacker, AttackType type) {
                return 0;
            }
        };

        // 注册实体属性附件（非玩家实体专用数据袋，core 自有）
        ENTITY_ATTRIBUTE_ATTACHMENT = defaultRegistry.getDeferredRegister().register(
                "entity_attribute_attachment",
                () -> AttachmentType.builder(EntityAttributeAttachment::new)
                        .serialize(EntityAttributeAttachment.CODEC)
                        .build()
        );

        // 注册生命属性（core 自有，与原版生命/死亡机制对接，任何配置下必然存在）
        // 其余游戏属性（力量、防御、暴击等）由 rpgcraftattributes 附属模块通过 IAttributeModule 提供
        defaultRegistry.register(LIFE_ID, "生命", "角色的生命值。归零即死亡，重生时恢复至上限。",
                100, 100, true, true);
        LIFE = defaultRegistry.getRawSupplier(LIFE_ID);

        // 注册玩家偏好设置附件（HUD 开关、战斗日志开关等，持久化保存）
        PLAYER_PREFERENCES = defaultRegistry.getDeferredRegister().register(
                "player_preferences",
                () -> AttachmentType.builder(PlayerPreferences::new)
                        .serialize(PlayerPreferences.CODEC)
                        .build()
        );
    }

    /**
     * RegisterEvent 回调：在 DeferredRegister 提交之前注册属性模块的 AttachmentType
     * <p>
     * 此方法由 {@link com.rpgcraft.core.RPGCraftCore} 构造函数通过
     * {@code modEventBus.addListener(AttributeManager::onRegisterAttachmentTypes)} 注册，
     * 且必须在 {@code getDeferredRegister().register(modEventBus)} 之前注册，
     * 以确保本监听器在 DeferredRegister 的监听器之前执行。
     * <p>
     * 执行流程：
     * <ol>
     *   <li>查询 {@link RPGSystems#getAttributeModule()} 获取获胜的属性模块</li>
     *   <li>调用获胜模块的 {@link IAttributeModule#registerAttributes(DefaultAttributeRegistry)}</li>
     * </ol>
     * 生命属性（LIFE）已在 {@link #init()} 中由 core 自行注册，不依赖任何属性模块。
     *
     * @param event NeoForge 注册事件
     */
    public static void onRegisterAttachmentTypes(RegisterEvent event) {
        if (!event.getRegistryKey().equals(NeoForgeRegistries.Keys.ATTACHMENT_TYPES)) return;

        IAttributeModule module = RPGSystems.getAttributeModule();
        if (module != null) {
            module.registerAttributes(defaultRegistry);
        }
    }

    /**
     * 获取属性注册中心（接口类型）
     * <p>
     * 与装备模块的 {@code EquipmentManager.getRegistry()} 返回接口类型保持一致。
     */
    public static IAttributeRegistry getRegistry() {
        return defaultRegistry;
    }

    /**
     * 获取底层 DeferredRegister，用于注册到 Mod 事件总线
     * <p>
     * 与装备模块的 {@code EquipmentData.getAttachmentRegister()} 模式对齐。
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
    // 生命属性（core 自有，与原版机制对接）
    // ====================================================================

    /**
     * 生命属性标识符（core 自有，任何配置下必然存在）
     * <p>
     * 真相源为 {@link AttributeIds#LIFE_ID}；此处保留为便捷别名以维持向后兼容。
     */
    public static final Identifier LIFE_ID = AttributeIds.LIFE_ID;

    /**
     * 生命属性附件 Supplier（由 {@link #init()} 注册后填充）
     * <p>
     * 永不为 null —— LIFE 由 core 直接注册，任何配置下必然存在。
     */
    public static Supplier<AttachmentType<EntityAttribute>> LIFE;

    // ====================================================================
    // 实体属性附件（非玩家实体专用）
    // ====================================================================

    /**
     * 实体属性附件 Supplier
     * <p>
     * 挂载到所有 {@link net.minecraft.world.entity.LivingEntity} 上，
     * 存储非玩家实体的固有属性基础值和持久修饰符。
     * 玩家属性继续使用各游戏属性模块注册的 {@link EntityAttribute} 附件。
     *
     * @see EntityAttributeAttachment
     */
    public static Supplier<AttachmentType<EntityAttributeAttachment>> ENTITY_ATTRIBUTE_ATTACHMENT;

    // ====================================================================
    // 玩家偏好设置（HUD 开关、战斗日志开关等）
    // ====================================================================

    /**
     * 玩家偏好设置附件 Supplier
     * <p>
     * 挂载到玩家实体上，存储每玩家的个性化开关状态（HUD、战斗日志等），
     * 通过附件系统自动持久化到存档。
     *
     * @see com.rpgcraft.core.preference.PlayerPreferences
     */
    public static Supplier<AttachmentType<PlayerPreferences>> PLAYER_PREFERENCES;

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
        if (LIFE == null) return;
        EntityAttribute lifeAttr = player.getData(LIFE);
        if (lifeAttr.getMaxValue() <= 0) return; // 避免除零
        // 按比例映射：custom_value / custom_max = vanilla_health / vanilla_max
        float scaledHealth = (float) lifeAttr.getValue() / lifeAttr.getMaxValue() * player.getMaxHealth();
        player.setHealth(Math.min(scaledHealth, player.getMaxHealth()));
    }

    // ====================================================================
    // 查询方法（委托到注册中心）
    // ====================================================================

    /**
     * 根据属性 ID 获取附件类型
     * <p>
     * 若属性 ID 不存在（模组被卸载），返回 null 并记录 WARN 日志，
     * 而不是抛出 NullPointerException。
     *
     * @param id 属性标识符
     * @return 附件类型，若不存在返回 null
     */
    public static AttachmentType<EntityAttribute> getTypeById(Identifier id) {
        Supplier<AttachmentType<EntityAttribute>> supplier = defaultRegistry.getRawSupplier(id);
        if (supplier == null) {
            return null;
        }
        return supplier.get();
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
