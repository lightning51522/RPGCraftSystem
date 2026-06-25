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

    /**
     * core 兜底实现的优先级
     * <p>
     * 低于 {@link #DEFAULT_PRIORITY}，用于在无任何插件加载时为各系统槽预填充 no-op 兜底实现，
     * 保证 core 能独立运行不抛 {@link IllegalStateException}（与 {@code IDamageCalculator} 的
     * 透传兜底、{@code ExperienceCurveManager} 的默认曲线同一哲学）。
     * <p>
     * 官方模块以 {@link #DEFAULT_PRIORITY}（0）注册时优先级更高，会正常覆盖此兜底。
     */
    public static final int FALLBACK_PRIORITY = -100;

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
    private static Registration<com.rpgcraft.core.profession.api.IProfessionRegistry> professionRegistry;
    private static Registration<ICombatSystem> combatSystem;
    private static Registration<IAttackTypeResolver> attackTypeResolver;
    private static Registration<IMobDataProvider> mobDataProvider;
    private static Registration<IClientSystem> clientSystem;
    private static Registration<IAttributeModule> attributeModule;
    private static Registration<Supplier<?>> playerLevelAttachment;

    // 职业系统
    private static Registration<Supplier<?>> playerProfessionAttachment;

    // 属性点系统
    private static Registration<IAttributePointSystem> attributePointSystem;
    private static Registration<Supplier<?>> playerAttributePointsAttachment;

    // 技能系统
    private static Registration<ISkillSystem> skillSystem;
    private static Registration<Supplier<?>> playerSkillsAttachment;

    /**
     * 静态初始化：为缺乏官方实现的 6 个系统槽预填充 no-op 兜底（优先级 {@link #FALLBACK_PRIORITY}）。
     * <p>
     * 这样无对应插件时 core 仍能独立运行（getter 不抛 {@link IllegalStateException}），
     * 官方模块以 {@link #DEFAULT_PRIORITY} 注册时优先级更高，会正常覆盖兜底。
     * <ul>
     *   <li>professionSystem / attributePointSystem / skillSystem 已有 {@code has*()} 守卫，
     *       且调用方约定缺失即跳过，故<b>不</b>预填充（保留 has*() 语义）。</li>
     *   <li>attributeModule 的 getter 已是 null 容忍，也<b>不</b>预填充。</li>
     * </ul>
     */
    static {
        levelSystem = new Registration<>(new com.rpgcraft.core.registry.defaults.NoOpLevelSystem(), FALLBACK_PRIORITY);
        equipmentSystem = new Registration<>(new com.rpgcraft.core.registry.defaults.NoOpEquipmentSystem(), FALLBACK_PRIORITY);
        combatSystem = new Registration<>(new com.rpgcraft.core.registry.defaults.NoOpCombatSystem(), FALLBACK_PRIORITY);
        attackTypeResolver = new Registration<>(new com.rpgcraft.core.registry.defaults.NoOpAttackTypeResolver(), FALLBACK_PRIORITY);
        mobDataProvider = new Registration<>(new com.rpgcraft.core.registry.defaults.NoOpMobDataProvider(), FALLBACK_PRIORITY);
        clientSystem = new Registration<>(new com.rpgcraft.core.registry.defaults.NoOpClientSystem(), FALLBACK_PRIORITY);
    }

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
     * 到 {@code Supplier<AttachmentType<T>>} 的泛型强转（消除各调用处的 {@code @SuppressWarnings("unchecked")}）。
     * <p>
     * 入参为 {@link Registration}（附件槽已纳入优先级机制），从中取出实现并强转。
     *
     * @param name 附件名（用于异常信息）
     * @param reg  注册条目（持有 raw supplier）
     * @param <T>  附件数据类型
     * @return 附件类型 Supplier
     */
    @SuppressWarnings("unchecked")
    private static <T> java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>>
            requireAttachment(String name, Registration<java.util.function.Supplier<?>> reg) {
        if (reg == null) {
            throw new IllegalStateException(name + " 未注册，请先由对应模块初始化");
        }
        return (java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<T>>) reg.implementation();
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
     * 注册职业注册中心实现（默认优先级）
     * <p>
     * 由 {@code profession} 模块调用，供内容模块（{@code professions}）及第三方内容包通过
     * {@link #getProfessionRegistry()} 获取注册中心并注册自定义职业，而无需编译期依赖
     * {@code profession} 插件（只依赖 core）。
     *
     * @param registry 职业注册中心实例
     */
    public static void registerProfessionRegistry(com.rpgcraft.core.profession.api.IProfessionRegistry registry) {
        registerProfessionRegistry(registry, DEFAULT_PRIORITY);
    }

    /**
     * 注册职业注册中心实现（指定优先级）
     *
     * @param registry 职业注册中心实例
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerProfessionRegistry(com.rpgcraft.core.profession.api.IProfessionRegistry registry, int priority) {
        if (shouldOverride("IProfessionRegistry", professionRegistry, priority)) {
            professionRegistry = new Registration<>(registry, priority);
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
     * 注册玩家等级附件类型 Supplier（默认优先级）
     * <p>
     * 由 leveling 模块调用，供客户端代码通过 {@link #getPlayerLevelAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<PlayerLevelData> 的 Supplier
     */
    public static void registerPlayerLevelAttachment(Supplier<?> supplier) {
        registerPlayerLevelAttachment(supplier, DEFAULT_PRIORITY);
    }

    /**
     * 注册玩家等级附件类型 Supplier（指定优先级）
     *
     * @param supplier AttachmentType<PlayerLevelData> 的 Supplier
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerPlayerLevelAttachment(Supplier<?> supplier, int priority) {
        if (shouldOverride("playerLevelAttachment", playerLevelAttachment, priority)) {
            playerLevelAttachment = new Registration<>(supplier, priority);
        }
    }

    /**
     * 注册玩家职业附件类型 Supplier（默认优先级）
     * <p>
     * 由 profession 模块调用，供客户端代码通过 {@link #getPlayerProfessionAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<ProfessionData> 的 Supplier
     */
    public static void registerPlayerProfessionAttachment(Supplier<?> supplier) {
        registerPlayerProfessionAttachment(supplier, DEFAULT_PRIORITY);
    }

    /**
     * 注册玩家职业附件类型 Supplier（指定优先级）
     *
     * @param supplier AttachmentType<ProfessionData> 的 Supplier
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerPlayerProfessionAttachment(Supplier<?> supplier, int priority) {
        if (shouldOverride("playerProfessionAttachment", playerProfessionAttachment, priority)) {
            playerProfessionAttachment = new Registration<>(supplier, priority);
        }
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
     * 注册玩家属性点附件类型 Supplier（默认优先级）
     * <p>
     * 由 attributepoints 模块调用，供客户端 UI 通过 {@link #getPlayerAttributePointsAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<PlayerAttributePoints> 的 Supplier
     */
    public static void registerPlayerAttributePointsAttachment(Supplier<?> supplier) {
        registerPlayerAttributePointsAttachment(supplier, DEFAULT_PRIORITY);
    }

    /**
     * 注册玩家属性点附件类型 Supplier（指定优先级）
     *
     * @param supplier AttachmentType<PlayerAttributePoints> 的 Supplier
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerPlayerAttributePointsAttachment(Supplier<?> supplier, int priority) {
        if (shouldOverride("playerAttributePointsAttachment", playerAttributePointsAttachment, priority)) {
            playerAttributePointsAttachment = new Registration<>(supplier, priority);
        }
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
     * 注册玩家技能附件类型 Supplier（默认优先级）
     * <p>
     * 由 skills 模块调用，供客户端 UI 通过 {@link #getPlayerSkillsAttachment()} 获取附件类型。
     *
     * @param supplier AttachmentType<PlayerSkillData> 的 Supplier
     */
    public static void registerPlayerSkillsAttachment(Supplier<?> supplier) {
        registerPlayerSkillsAttachment(supplier, DEFAULT_PRIORITY);
    }

    /**
     * 注册玩家技能附件类型 Supplier（指定优先级）
     *
     * @param supplier AttachmentType<PlayerSkillData> 的 Supplier
     * @param priority 注册优先级（数值越高越优先）
     */
    public static void registerPlayerSkillsAttachment(Supplier<?> supplier, int priority) {
        if (shouldOverride("playerSkillsAttachment", playerSkillsAttachment, priority)) {
            playerSkillsAttachment = new Registration<>(supplier, priority);
        }
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
     * 获取职业注册中心
     * <p>
     * 供内容模块（{@code professions}）及第三方内容包注册自定义职业。
     * 由 {@code profession} 模块注册。
     *
     * @return 职业注册中心实例
     * @throws IllegalStateException 未注册时抛出（{@code profession} 模块未加载）
     */
    public static com.rpgcraft.core.profession.api.IProfessionRegistry getProfessionRegistry() {
        return requireSystem("IProfessionRegistry", professionRegistry);
    }

    /**
     * 查询职业注册中心是否已注册（避免内容模块在引擎未加载时空指针）
     *
     * @return {@code true} 已注册
     */
    public static boolean hasProfessionRegistry() {
        return professionRegistry != null;
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
