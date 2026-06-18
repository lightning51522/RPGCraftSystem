package com.rpgcraft.core.registry;

import com.rpgcraft.core.attribute.api.IAttributeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * RPG 系统统一注册门面
 * <p>
 * 各插件模块通过此类注册自己的系统接口实现，
 * 实现跨模块查询的解耦。插件模块在自己的 {@code init()} 方法中调用
 * {@code registerXxx()} 方法注册实现，其他模块通过 {@code getXxx()} 方法查询。
 * <p>
 * <b>优先级覆盖机制</b>：注册时指定优先级，高优先级可覆盖低优先级的实现。
 * <ul>
 *   <li>{@link #DEFAULT_PRIORITY}（0）：官方模块的默认优先级</li>
 *   <li>{@link #OVERRIDE_PRIORITY}（100）：第三方替代模块的覆盖优先级</li>
 * </ul>
 * 同优先级时第二次注册会被拒绝并输出 WARN 日志，防止意外覆盖。
 * <p>
 * 使用模式：
 * <pre>
 * // 官方模块注册（默认优先级）
 * RPGSystems.registerLevelSystem(system);
 *
 * // 第三方模块覆盖（高优先级）
 * RPGSystems.registerLevelSystem(system, RPGSystems.OVERRIDE_PRIORITY);
 *
 * // 查询（在需要跨模块访问时）
 * RPGSystems.getLevelSystem().getLevel(player);
 * </pre>
 *
 * @see ILevelSystem
 * @see IEquipmentSystem
 * @see IProfessionSystem
 * @see ICombatSystem
 * @see IAttackTypeResolver
 * @see IMobDataProvider
 */
public final class RPGSystems {

    private static final Logger LOGGER = LoggerFactory.getLogger(RPGSystems.class);

    /**
     * 官方模块的默认优先级
     * <p>
     * 不带 priority 参数的 {@code register} 方法使用此优先级。
     */
    public static final int DEFAULT_PRIORITY = 0;

    /**
     * 第三方替代模块的覆盖优先级
     * <p>
     * 高于此优先级的注册会覆盖已注册的实现。
     */
    public static final int OVERRIDE_PRIORITY = 100;

    // ====================================================================
    // 注册条目（实现 + 优先级）
    // ====================================================================

    /**
     * 注册条目，包含系统实现和注册优先级
     *
     * @param <T> 系统接口类型
     */
    private record Registration<T>(T implementation, int priority) {}

    private static Registration<ILevelSystem> levelSystem;
    private static Registration<IEquipmentSystem> equipmentSystem;
    private static Registration<IProfessionSystem> professionSystem;
    private static Registration<ICombatSystem> combatSystem;
    private static Registration<IAttackTypeResolver> attackTypeResolver;
    private static Registration<IMobDataProvider> mobDataProvider;
    private static Registration<IClientSystem> clientSystem;
    private static Registration<IAttributeModule> attributeModule;
    private static Supplier<?> playerLevelAttachment;
    private static Supplier<?> playerProfessionAttachment;

    // 属性点系统
    private static Registration<IAttributePointSystem> attributePointSystem;
    private static Supplier<?> playerAttributePointsAttachment;

    // 技能系统
    private static Registration<ISkillSystem> skillSystem;
    private static Supplier<?> playerSkillsAttachment;

    private RPGSystems() {
        // 禁止实例化
    }

    // ====================================================================
    // 优先级检查工具
    // ====================================================================

    /**
     * 检查新注册是否应该覆盖现有注册
     *
     * @param name        系统名称（用于日志）
     * @param existing    现有注册条目
     * @param newPriority 新注册的优先级
     * @param <T>         系统接口类型
     * @return true 如果新注册应该覆盖现有注册
     */
    private static <T> boolean shouldOverride(String name, Registration<T> existing, int newPriority) {
        if (existing == null) {
            return true;
        }
        if (newPriority > existing.priority()) {
            LOGGER.info("{}：优先级 {} > 已注册优先级 {}，覆盖现有实现", name, newPriority, existing.priority());
            return true;
        }
        if (newPriority == existing.priority()) {
            LOGGER.warn("{}：优先级相同（{}），拒绝覆盖。如需覆盖请使用更高优先级（如 RPGSystems.OVERRIDE_PRIORITY）",
                    name, newPriority);
            return false;
        }
        LOGGER.info("{}：优先级 {} < 已注册优先级 {}，保留现有实现", name, newPriority, existing.priority());
        return false;
    }

    /**
     * 查询系统实现，未注册时抛 {@link IllegalStateException}。消除各 getXxx 方法的 null 检查样板。
     *
     * @param name 系统名（用于异常信息）
     * @param reg  注册条目
     * @param <T>  系统接口类型
     * @return 系统实现
     */
    private static <T> T requireSystem(String name, Registration<T> reg) {
        if (reg == null) {
            throw new IllegalStateException(name + " 未注册，请先调用对应 register 方法");
        }
        return reg.implementation();
    }

    /**
     * 查询附件 Supplier，未注册时抛 {@link IllegalStateException}，并集中处理 raw {@code Supplier<?>}
     * 到 {@code Supplier<AttachmentType<T>>} 的泛型强转（消除 3 处 {@code @SuppressWarnings("unchecked")}）。
     *
     * @param name 附件名（用于异常信息）
     * @param raw  原始 raw supplier
     * @param <T>  附件数据类型
     * @return 附件类型 Supplier
     */
    @SuppressWarnings("unchecked")
    private static <T> java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>>
            requireAttachment(String name, java.util.function.Supplier<?> raw) {
        if (raw == null) {
            throw new IllegalStateException(name + " 未注册，请先由对应模块初始化");
        }
        return (java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>>) raw;
    }

    // ====================================================================
    // 注册方法（各插件模块在 init() 中调用）
    // ====================================================================

    /**
     * 注册等级系统实现（默认优先级）
     *
     * @param system 等级系统实例
     */
    public static void registerLevelSystem(ILevelSystem system) {
        registerLevelSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册等级系统实现（指定优先级）
     *
     * @param system   等级系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerLevelSystem(ILevelSystem system, int priority) {
        if (shouldOverride("ILevelSystem", levelSystem, priority)) {
            levelSystem = new Registration<>(system, priority);
        }
    }

    /**
     * 注册装备系统实现（默认优先级）
     *
     * @param system 装备系统实例
     */
    public static void registerEquipmentSystem(IEquipmentSystem system) {
        registerEquipmentSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册装备系统实现（指定优先级）
     *
     * @param system   装备系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerEquipmentSystem(IEquipmentSystem system, int priority) {
        if (shouldOverride("IEquipmentSystem", equipmentSystem, priority)) {
            equipmentSystem = new Registration<>(system, priority);
        }
    }

    /**
     * 注册职业系统实现（默认优先级）
     *
     * @param system 职业系统实例
     */
    public static void registerProfessionSystem(IProfessionSystem system) {
        registerProfessionSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册职业系统实现（指定优先级）
     *
     * @param system   职业系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerProfessionSystem(IProfessionSystem system, int priority) {
        if (shouldOverride("IProfessionSystem", professionSystem, priority)) {
            professionSystem = new Registration<>(system, priority);
        }
    }

    /**
     * 注册战斗系统实现（默认优先级）
     *
     * @param system 战斗系统实例
     */
    public static void registerCombatSystem(ICombatSystem system) {
        registerCombatSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册战斗系统实现（指定优先级）
     *
     * @param system   战斗系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerCombatSystem(ICombatSystem system, int priority) {
        if (shouldOverride("ICombatSystem", combatSystem, priority)) {
            combatSystem = new Registration<>(system, priority);
        }
    }

    /**
     * 注册攻击类型解析器（默认优先级）
     *
     * @param resolver 攻击类型解析器实例
     */
    public static void registerAttackTypeResolver(IAttackTypeResolver resolver) {
        registerAttackTypeResolver(resolver, DEFAULT_PRIORITY);
    }

    /**
     * 注册攻击类型解析器（指定优先级）
     *
     * @param resolver 攻击类型解析器实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerAttackTypeResolver(IAttackTypeResolver resolver, int priority) {
        if (shouldOverride("IAttackTypeResolver", attackTypeResolver, priority)) {
            attackTypeResolver = new Registration<>(resolver, priority);
        }
    }

    /**
     * 注册怪物数据提供者（默认优先级）
     *
     * @param provider 怪物数据提供者实例
     */
    public static void registerMobDataProvider(IMobDataProvider provider) {
        registerMobDataProvider(provider, DEFAULT_PRIORITY);
    }

    /**
     * 注册怪物数据提供者（指定优先级）
     *
     * @param provider 怪物数据提供者实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerMobDataProvider(IMobDataProvider provider, int priority) {
        if (shouldOverride("IMobDataProvider", mobDataProvider, priority)) {
            mobDataProvider = new Registration<>(provider, priority);
        }
    }

    /**
     * 注册客户端系统实现（默认优先级）
     * <p>
     * 由 client 模块调用，供 core 中的命令系统通过此接口发送客户端同步包。
     *
     * @param system 客户端系统实例
     */
    public static void registerClientSystem(IClientSystem system) {
        registerClientSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册客户端系统实现（指定优先级）
     *
     * @param system   客户端系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerClientSystem(IClientSystem system, int priority) {
        if (shouldOverride("IClientSystem", clientSystem, priority)) {
            clientSystem = new Registration<>(system, priority);
        }
    }

    // ====================================================================
    // 属性模块注册
    // ====================================================================

    /**
     * 注册属性模块实现（默认优先级）
     * <p>
     * 默认属性模块由 {@code rpgcraftattributes} 提供。
     * 第三方模组可使用 {@link #registerAttributeModule(IAttributeModule, int)} 指定更高优先级来替换。
     *
     * @param module 属性模块实例
     */
    public static void registerAttributeModule(IAttributeModule module) {
        registerAttributeModule(module, DEFAULT_PRIORITY);
    }

    /**
     * 注册属性模块实现（指定优先级）
     * <p>
     * 属性模块决定了哪些 RPG 属性被注册到系统中。默认模块注册全部 12 个属性；
     * 第三方模块可以只注册需要的属性子集。
     * <p>
     * 优先级规则与其他系统注册一致：高优先级覆盖低优先级，同优先级拒绝覆盖。
     *
     * @param module   属性模块实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerAttributeModule(IAttributeModule module, int priority) {
        if (shouldOverride("IAttributeModule", attributeModule, priority)) {
            attributeModule = new Registration<>(module, priority);
        }
    }

    /**
     * 注册玩家等级附件类型 Supplier
     * <p>
     * 由 leveling 模块调用，供客户端代码通过 {@link #getPlayerLevelAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<PlayerLevelData> 的 Supplier
     */
    public static void registerPlayerLevelAttachment(Supplier<?> supplier) {
        playerLevelAttachment = supplier;
    }

    /**
     * 注册玩家职业附件类型 Supplier
     * <p>
     * 由 profession 模块调用，供客户端代码通过 {@link #getPlayerProfessionAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<ProfessionData> 的 Supplier
     */
    public static void registerPlayerProfessionAttachment(Supplier<?> supplier) {
        playerProfessionAttachment = supplier;
    }

    /**
     * 注册属性点系统实现（默认优先级）
     *
     * @param system 属性点系统实例
     */
    public static void registerAttributePointSystem(IAttributePointSystem system) {
        registerAttributePointSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册属性点系统实现（指定优先级）
     *
     * @param system   属性点系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerAttributePointSystem(IAttributePointSystem system, int priority) {
        if (shouldOverride("IAttributePointSystem", attributePointSystem, priority)) {
            attributePointSystem = new Registration<>(system, priority);
        }
    }

    /**
     * 注册玩家属性点附件类型 Supplier
     * <p>
     * 由 attributepoints 模块调用，供客户端 UI 通过 {@link #getPlayerAttributePointsAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<PlayerAttributePoints> 的 Supplier
     */
    public static void registerPlayerAttributePointsAttachment(Supplier<?> supplier) {
        playerAttributePointsAttachment = supplier;
    }

    /**
     * 注册技能系统实现（默认优先级）
     *
     * @param system 技能系统实例
     */
    public static void registerSkillSystem(ISkillSystem system) {
        registerSkillSystem(system, DEFAULT_PRIORITY);
    }

    /**
     * 注册技能系统实现（指定优先级）
     *
     * @param system   技能系统实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerSkillSystem(ISkillSystem system, int priority) {
        if (shouldOverride("ISkillSystem", skillSystem, priority)) {
            skillSystem = new Registration<>(system, priority);
        }
    }

    /**
     * 注册玩家技能附件类型 Supplier
     * <p>
     * 由 skills 模块调用，供客户端 UI 通过 {@link #getPlayerSkillsAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<PlayerSkillData> 的 Supplier
     */
    public static void registerPlayerSkillsAttachment(Supplier<?> supplier) {
        playerSkillsAttachment = supplier;
    }

    // ====================================================================
    // 查询方法（跨模块访问，未注册时抛出 IllegalStateException）
    // ====================================================================

    /**
     * 获取等级系统
     *
     * @return 等级系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static ILevelSystem getLevelSystem() {
        return requireSystem("ILevelSystem", levelSystem);
    }

    /**
     * 获取装备系统
     *
     * @return 装备系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static IEquipmentSystem getEquipmentSystem() {
        return requireSystem("IEquipmentSystem", equipmentSystem);
    }

    /**
     * 查询职业系统是否已注册（避免 UI 在模块未加载时空指针）
     *
     * @return {@code true} 已注册
     */
    public static boolean hasProfessionSystem() {
        return professionSystem != null;
    }

    /**
     * 获取职业系统
     *
     * @return 职业系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static IProfessionSystem getProfessionSystem() {
        return requireSystem("IProfessionSystem", professionSystem);
    }

    /**
     * 获取战斗系统
     *
     * @return 战斗系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static ICombatSystem getCombatSystem() {
        return requireSystem("ICombatSystem", combatSystem);
    }

    /**
     * 获取攻击类型解析器
     *
     * @return 攻击类型解析器实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static IAttackTypeResolver getAttackTypeResolver() {
        return requireSystem("IAttackTypeResolver", attackTypeResolver);
    }

    /**
     * 获取怪物数据提供者
     *
     * @return 怪物数据提供者实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static IMobDataProvider getMobDataProvider() {
        return requireSystem("IMobDataProvider", mobDataProvider);
    }

    /**
     * 获取客户端系统
     *
     * @return 客户端系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static IClientSystem getClientSystem() {
        return requireSystem("IClientSystem", clientSystem);
    }

    /**
     * 获取属性模块
     * <p>
     * 可能为 null（无属性模块时），调用方需自行处理。
     *
     * @return 属性模块实例，未注册时返回 null
     */
    public static IAttributeModule getAttributeModule() {
        return attributeModule != null ? attributeModule.implementation() : null;
    }

    /**
     * 获取玩家等级附件类型 Supplier
     * <p>
     * 用于客户端代码访问玩家等级数据附件。
     *
     * @param <T> 附件数据类型（PlayerLevelData）
     * @return 附件类型 Supplier
     * @throws IllegalStateException 未注册时抛出
     */
    public static <T> Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>> getPlayerLevelAttachment() {
        return requireAttachment("playerLevelAttachment", playerLevelAttachment);
    }

    /**
     * 获取玩家职业附件类型 Supplier
     * <p>
     * 用于客户端代码访问玩家职业数据附件。
     *
     * @param <T> 附件数据类型（ProfessionData）
     * @return 附件类型 Supplier
     * @throws IllegalStateException 未注册时抛出
     */
    public static <T> Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>> getPlayerProfessionAttachment() {
        return requireAttachment("playerProfessionAttachment", playerProfessionAttachment);
    }

    /**
     * 获取属性点系统
     *
     * @return 属性点系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static IAttributePointSystem getAttributePointSystem() {
        return requireSystem("IAttributePointSystem", attributePointSystem);
    }

    /**
     * 查询属性点系统是否已注册（避免 UI 在模块未加载时空指针）
     *
     * @return {@code true} 已注册
     */
    public static boolean hasAttributePointSystem() {
        return attributePointSystem != null;
    }

    /**
     * 获取玩家属性点附件类型 Supplier
     * <p>
     * 用于客户端 UI 访问玩家属性点数据附件。
     *
     * @param <T> 附件数据类型（PlayerAttributePoints）
     * @return 附件类型 Supplier
     * @throws IllegalStateException 未注册时抛出
     */
    public static <T> Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>> getPlayerAttributePointsAttachment() {
        return requireAttachment("playerAttributePointsAttachment", playerAttributePointsAttachment);
    }

    /**
     * 获取技能系统
     *
     * @return 技能系统实例
     * @throws IllegalStateException 未注册时抛出
     */
    public static ISkillSystem getSkillSystem() {
        return requireSystem("ISkillSystem", skillSystem);
    }

    /**
     * 查询技能系统是否已注册（避免 UI 在模块未加载时空指针）
     *
     * @return {@code true} 已注册
     */
    public static boolean hasSkillSystem() {
        return skillSystem != null;
    }

    /**
     * 获取玩家技能附件类型 Supplier
     * <p>
     * 用于客户端 UI 访问玩家技能数据附件。
     *
     * @param <T> 附件数据类型（PlayerSkillData）
     * @return 附件类型 Supplier
     * @throws IllegalStateException 未注册时抛出
     */
    public static <T> Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>> getPlayerSkillsAttachment() {
        return requireAttachment("playerSkillsAttachment", playerSkillsAttachment);
    }
}
