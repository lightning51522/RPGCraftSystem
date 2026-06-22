package com.rpgcraft.profession;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeModifier;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.combat.RPGDamageEvent;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.network.ProfessionStateAssembler;
import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.ProfessionCombatContext;
import com.rpgcraft.core.profession.api.ProfessionContext;
import com.rpgcraft.core.registry.IProfessionSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.profession.network.SyncPlayerProfessionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 职业模块全局门面
 * <p>
 * 保留对 {@link ProfessionRegistry} 和 {@link ProfessionData} 附件的静态引用。
 * 实现职业经验/等级、进阶树、主职业切换、副职业等逻辑。
 * <p>
 * 加成规则：仅「当前主职业」（按其等级）与「已激活的副职业」（按副职业等级）的加成生效。
 * 其他已解锁但非当前/副的职业不提供加成。加成按 {@link IProfession#getBonusAtLevel} 线性计算。
 *
 * @apiNote 内部 API — 第三方模组应通过 {@link RPGSystems} 门面访问，不应直接依赖此类。
 */
public class ProfessionManager {

    /** 全局职业默认等级上限（职业未指定 max_level 时使用；由 ProfessionConfigLoader 加载） */
    public static final int LEVEL_CAP = 20;

    private static ProfessionRegistry registry;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    // ====================================================================
    // 职业标识符常量
    // ====================================================================

    public static final Identifier COMMONER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "commoner");
    /** 占位副职业 ID（当 professions 子模块未提供任何 secondary 职业时由子模块注入） */
    public static final Identifier APPRENTICE_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "apprentice");
    /**
     * @deprecated 具体职业已迁移到 professions 子模块的 Java 类，这些 ID 常量仅保留供历史代码引用兼容；
     * 新代码应通过 {@link #getRegistry()} 按需查找。
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier WARRIOR_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "warrior");
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier ARCHER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "archer");
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier BERSERKER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "berserker");
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    public static final Identifier MARKSMAN_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "marksman");

    // ====================================================================
    // AttachmentType Supplier
    // ====================================================================

    public static Supplier<AttachmentType<ProfessionData>> PLAYER_PROFESSION;

    /**
     * 初始化职业模块
     * <p>
     * 具体职业定义由 {@code professions} 子模块（{@code rpgcraftprofessions}）在它的
     * {@code @Mod} 入口构造函数中通过 {@link #getRegistry()} 注册，依赖顺序保证
     * 本方法先于 professions 模块的初始化执行。
     * <p>
     * 本方法负责：创建注册表、注册附件类型、注册 SPI/系统门面/状态组装器、
     * 注册战斗钩子调度监听器（RPGEventBus）。
     */
    public static void init() {
        registry = new ProfessionRegistry();

        deferredRegister = DeferredRegister.create(
                NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_PROFESSION = deferredRegister.register("player_profession",
                () -> AttachmentType.builder(ProfessionData::new)
                        .serialize(ProfessionData.CODEC)
                        .build()
        );

        RPGSystems.registerPlayerProfessionAttachment(PLAYER_PROFESSION);

        RPGSystems.registerProfessionSystem(new ProfessionSystemImpl());

        // 注册职业状态组装器（供 core 的 RequestProfessionStatePacket 组装完整状态推送给客户端）
        ProfessionStateAssembler.register(ProfessionManager::buildStateView);

        // 注册战斗钩子中央调度器：把 RPGDamageEvent 转发到玩家当前生效职业的 onAttack/onDamaged/onKill
        registerCombatDispatchers();
    }

    /**
     * 组装玩家的完整职业状态视图（供职业面板渲染）
     */
    public static com.rpgcraft.core.ui.ProfessionStateCache.ProfessionStateView buildStateView(ServerPlayer player) {
        ProfessionData data = getData(player);
        // 职业树节点元数据（与玩家无关）
        java.util.List<com.rpgcraft.core.ui.ProfessionStateCache.ProfessionNode> nodes = new java.util.ArrayList<>();
        for (IProfession prof : registry.getAllProfessions()) {
            nodes.add(new com.rpgcraft.core.ui.ProfessionStateCache.ProfessionNode(
                    prof.getId(), prof.getDisplayName(), prof.getDescription(),
                    prof.getPrerequisite(), prof.isAdvanced(), prof.getType(), prof.getMaxLevel(),
                    prof.getIconItem(), prof.getIconChar()));
        }
        return new com.rpgcraft.core.ui.ProfessionStateCache.ProfessionStateView(
                data.getSkillPointPool(),
                data.getProfessionId(),
                new java.util.LinkedHashSet<>(data.getActiveSecondaryProfessions()),
                new java.util.LinkedHashMap<>(data.getProfessionLevels()),
                new java.util.LinkedHashSet<>(data.getUnlockedProfessions()),
                java.util.Collections.unmodifiableList(nodes)
        );
    }

    /**
     * 向所有在线玩家推送最新职业状态。
     * <p>
     * 由 {@link ProfessionConfigLoader} 在 {@code /reload} 重建配置后调用，
     * 使客户端职业面板立即反映配置修改。
     */
    public static void pushProfessionStateToAllOnline() {
        MinecraftServer server = currentServer;
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                syncToClient(player);
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("推送职业状态给 {} 失败: {}", player.getName().getString(), e.getMessage());
            }
        }
    }

    /** 当前服务器实例（由 {@link ProfessionConfigLoader} 在 ServerStartedEvent 时注入） */
    private static volatile MinecraftServer currentServer;

    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void setCurrentServer(MinecraftServer server) {
        currentServer = server;
    }

    public static ProfessionRegistry getRegistry() {
        return registry;
    }

    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    // ====================================================================
    // 查询
    // ====================================================================

    public static IProfession getProfession(ServerPlayer player) {
        ProfessionData data = player.getData(PLAYER_PROFESSION);
        return registry.getProfession(data.getProfessionId());
    }

    public static ProfessionData getData(ServerPlayer player) {
        return player.getData(PLAYER_PROFESSION);
    }

    // ====================================================================
    // 职业经验与等级
    // ====================================================================

    /** 全局默认经验表：从 level 升到 level+1 所需经验，公式 round(50 × L^1.5)，L=1..LEVEL_CAP-1 */
    private static final int[] DEFAULT_EXP_TABLE = generateExpTable();

    private static int[] generateExpTable() {
        int cap = Math.max(2, ProfessionConfigLoader.getDefaultMaxLevel());
        // 委托 ExpFormula 统一公式（与 leveling 模块共用，避免拷贝漂移）
        return com.rpgcraft.core.level.ExpFormula.generateExpTable(cap);
    }

    /** 全局默认公式：从 level 升到 level+1 所需经验（不含职业专属表，向后兼容旧接口） */
    public static int getExpForNextLevel(int level) {
        if (level < 1) return Integer.MAX_VALUE;
        if (level - 1 >= DEFAULT_EXP_TABLE.length) return Integer.MAX_VALUE;
        return DEFAULT_EXP_TABLE[level - 1];
    }

    /**
     * 指定职业升到下一级所需经验。优先用职业专属经验表 {@link IProfession#getExpTable()}，
     * 缺失则回退到全局默认公式。
     */
    public static int getExpForNextLevel(IProfession prof, int level) {
        if (prof == null || level < 1 || level >= prof.getMaxLevel()) return Integer.MAX_VALUE;
        int[] table = prof.getExpTable();
        if (table != null) {
            int idx = level - 1;
            if (idx < 0 || idx >= table.length) return Integer.MAX_VALUE;
            return table[idx];
        }
        return getExpForNextLevel(level);
    }

    /** 玩家获得经验时把等量经验加进职业经验池（由 PlayerExpGainEvent 监听器调用，与等级经验同步） */
    public static void onPlayerLeveledUp(ServerPlayer player, int expGained) {
        if (expGained <= 0) return;
        ProfessionData data = getData(player);
        data.addSkillPoints(expGained);
        syncToClient(player);
    }

    public static boolean canInvest(ServerPlayer player, Identifier professionId) {
        ProfessionData data = getData(player);
        if (!data.isUnlocked(professionId)) return false;
        IProfession prof = registry.getProfession(professionId);
        if (prof == null) return false;
        int level = data.getProfessionLevel(professionId);
        if (level <= 0 || level >= prof.getMaxLevel()) return false;
        return data.getSkillPointPool() >= getExpForNextLevel(prof, level);
    }

    public static boolean investLevel(ServerPlayer player, Identifier professionId) {
        if (!canInvest(player, professionId)) return false;
        ProfessionData data = getData(player);
        IProfession prof = registry.getProfession(professionId);
        int level = data.getProfessionLevel(professionId);
        int cost = getExpForNextLevel(prof, level);
        data.addSkillPoints(-cost);
        data.setProfessionLevel(professionId, level + 1);

        // 若投入的是当前主职业，或某个已激活的副职业，重算其加成
        if (professionId.equals(data.getProfessionId())) {
            reapplyMainBonuses(player);
        } else if (data.isSecondaryActive(professionId)) {
            reapplySecondaryBonus(player, professionId);
        }
        // 生命周期钩子：等级提升
        if (prof != null) {
            prof.onLevelUp(new ProfessionContext(player, prof, level + 1));
        }
        syncToClient(player);
        return true;
    }

    // ====================================================================
    // 进阶（仅主职业参与）
    // ====================================================================

    public static boolean canAdvance(ServerPlayer player, Identifier professionId) {
        IProfession target = registry.getProfession(professionId);
        if (target == null || target.getPrerequisite() == null) return false;
        // 仅主职业可进阶
        if (target.getType() != IProfession.ProfessionType.PRIMARY) return false;
        ProfessionData data = getData(player);
        if (data.isUnlocked(professionId)) return false; // 已进阶
        Identifier prereq = target.getPrerequisite();
        if (!data.isUnlocked(prereq)) return false;
        IProfession prereqProf = registry.getProfession(prereq);
        int cap = prereqProf != null ? prereqProf.getMaxLevel() : LEVEL_CAP;
        return data.getProfessionLevel(prereq) >= cap;
    }

    public static boolean advance(ServerPlayer player, Identifier professionId) {
        if (!canAdvance(player, professionId)) return false;
        ProfessionData data = getData(player);
        IProfession target = registry.getProfession(professionId);
        // 解锁新职业（初始 1 级）
        data.unlock(professionId);
        // 切换当前主职业为进阶职业
        switchToMain(player, professionId);
        ProfessionMod.LOGGER.info("玩家 {} 进阶为 {}", player.getName().getString(),
                target.getDisplayName());
        // 生命周期钩子：进阶到本职业
        if (target != null) {
            target.onAdvance(new ProfessionContext(player, target,
                    data.getProfessionLevel(professionId)));
        }
        return true;
    }

    public static boolean isUnlocked(ServerPlayer player, Identifier professionId) {
        return getData(player).isUnlocked(professionId);
    }

    public static Collection<Identifier> getUnlocked(ServerPlayer player) {
        return getData(player).getUnlockedProfessions();
    }

    // ====================================================================
    // 主职业切换
    // ====================================================================

    /**
     * 是否可切换当前主职业到目标职业
     * <p>
     * 规则（见 {@link ProfessionConfigLoader#isAllowDowngradeSwitch()}）：
     * <ul>
     *   <li>目标必须已解锁，且必须是主职业（{@link IProfession.ProfessionType#PRIMARY}）</li>
     *   <li>目标不能是当前主职业本身</li>
     *   <li>从进阶职业切回其基础职业：受 {@code allow_downgrade_switch} 控制（默认禁止）</li>
     *   <li>其它切换允许（平民↔进阶、进阶叶子之间互切）</li>
     * </ul>
     */
    public static boolean canSwitchMain(ServerPlayer player, Identifier professionId) {
        ProfessionData data = getData(player);
        if (!data.isUnlocked(professionId)) return false;
        if (professionId.equals(data.getProfessionId())) return false;
        IProfession target = registry.getProfession(professionId);
        IProfession current = registry.getProfession(data.getProfessionId());
        if (target == null || current == null) return false;
        // 仅主职业可作为主职业切换目标
        if (target.getType() != IProfession.ProfessionType.PRIMARY) return false;

        // 目标是当前职业的基础职业 → 受切回开关控制
        boolean targetIsPrereqOfCurrent = professionId.equals(current.getPrerequisite());
        if (targetIsPrereqOfCurrent && !ProfessionConfigLoader.isAllowDowngradeSwitch()) {
            return false;
        }
        return true;
    }

    public static boolean switchMain(ServerPlayer player, Identifier professionId) {
        if (!canSwitchMain(player, professionId)) return false;
        switchToMain(player, professionId);
        return true;
    }

    /**
     * 内部切换主职业（不校验，已由调用方保证合法性）。
     * 移除旧主加成 → 更新职业 ID → 应用新主加成 → 同步。
     */
    private static void switchToMain(ServerPlayer player, Identifier professionId) {
        ProfessionData data = getData(player);
        IProfession oldProf = registry.getProfession(data.getProfessionId());
        IProfession newProf = registry.getProfession(professionId);
        if (oldProf != null) applyBonusAtLevel(player, oldProf, data.getProfessionLevel(oldProf.getId()), false, false);
        data.setProfessionId(professionId);
        if (newProf != null) applyBonusAtLevel(player, newProf, data.getProfessionLevel(professionId), true, false);
        syncToClient(player);
        if (oldProf != null && newProf != null) syncAffectedAttributes(player, oldProf, newProf);
    }

    // ====================================================================
    // 副职业（多副职业独立激活，加成共存）
    // ====================================================================

    /**
     * 获取已激活的副职业集合（不可变视图）。
     */
    public static Set<Identifier> getActiveSecondary(ServerPlayer player) {
        return getData(player).getActiveSecondaryProfessions();
    }

    public static boolean isSecondaryActive(ServerPlayer player, Identifier professionId) {
        return getData(player).isSecondaryActive(professionId);
    }

    /**
     * 设置某副职业的激活状态。
     * <p>
     * 服务端权威校验：目标必须存在、是 SECONDARY 类型、已解锁、且不等于当前主职业。
     * 通过校验后写入附件数据，并立即应用/移除该副职业的属性加成，最后同步到客户端。
     */
    public static void setSecondaryActive(ServerPlayer player, Identifier professionId, boolean active) {
        ProfessionData data = getData(player);
        IProfession prof = registry.getProfession(professionId);
        if (prof == null || prof.getType() != IProfession.ProfessionType.SECONDARY) return;
        if (!data.isUnlocked(professionId)) return;
        if (professionId.equals(data.getProfessionId())) return;
        if (active == data.isSecondaryActive(professionId)) return;
        data.setSecondaryActive(professionId, active);
        applyBonusAtLevel(player, prof, data.getProfessionLevel(professionId), active, true);
        // 生命周期钩子：副职业激活/取消
        ProfessionContext ctx = new ProfessionContext(player, prof, data.getProfessionLevel(professionId));
        if (active) prof.onActivate(ctx);
        else prof.onDeactivate(ctx);
        syncToClient(player);
    }

    // ----------------------------------------------------------------
    // 副职业解锁（消耗职业经验）
    // ----------------------------------------------------------------

    /**
     * 是否可以解锁指定副职业（仅校验，不消耗经验）。
     * <p>
     * 规则：
     * <ul>
     *   <li>目标必须是 SECONDARY 类型且尚未解锁</li>
     *   <li>基础副职业（prerequisite=null）：池内经验 ≥ {@link ProfessionConfigLoader#getSecondaryUnlockCost}</li>
     *   <li>非基础副职业：前置副职业须已解锁且达其满级，且池内经验 ≥ 解锁消耗</li>
     * </ul>
     */
    public static boolean canUnlockSecondary(ServerPlayer player, Identifier professionId) {
        IProfession prof = registry.getProfession(professionId);
        if (prof == null || prof.getType() != IProfession.ProfessionType.SECONDARY) return false;
        ProfessionData data = getData(player);
        if (data.isUnlocked(professionId)) return false; // 已解锁
        int cost = ProfessionConfigLoader.getSecondaryUnlockCost();
        if (data.getSkillPointPool() < cost) return false;
        // 前置校验：基础副职业无前置；非基础副职业需前置已解锁且满级
        Identifier prereq = prof.getPrerequisite();
        if (prereq != null) {
            if (!data.isUnlocked(prereq)) return false;
            IProfession prereqProf = registry.getProfession(prereq);
            int prereqMax = prereqProf != null ? prereqProf.getMaxLevel() : LEVEL_CAP;
            if (data.getProfessionLevel(prereq) < prereqMax) return false;
        }
        return true;
    }

    /**
     * 解锁指定副职业。服务端权威校验 + 扣经验 + 解锁（初始 1 级）+ 同步。
     * <p>
     * 注意：本方法仅完成"解锁"，不自动激活。玩家需在面板双击切换激活状态。
     * 失败返回 false（不消耗经验、不改变状态）。
     */
    public static boolean unlockSecondary(ServerPlayer player, Identifier professionId) {
        if (!canUnlockSecondary(player, professionId)) return false;
        ProfessionData data = getData(player);
        IProfession prof = registry.getProfession(professionId);
        int cost = ProfessionConfigLoader.getSecondaryUnlockCost();
        data.addSkillPoints(-cost);
        data.unlock(professionId); // 初始 1 级，加入已解锁集合
        ProfessionMod.LOGGER.info("玩家 {} 消耗 {} 经验解锁副职业 {}",
                player.getName().getString(), cost, prof.getDisplayName());
        syncToClient(player);
        return true;
    }

    // ====================================================================
    // 加成应用（按等级线性计算）
    // ====================================================================

    /** 修饰符来源前缀：主职业 */
    private static final String MAIN_PREFIX = "rpgcraftprofession";
    /** 修饰符来源前缀：副职业 */
    private static final String SECONDARY_PREFIX = "rpgcraftprofession_sec";

    private static Identifier modifierSourceId(Identifier attrId, boolean secondary) {
        String prefix = secondary ? SECONDARY_PREFIX : MAIN_PREFIX;
        return Identifier.fromNamespaceAndPath(prefix,
                "bonus_" + attrId.getNamespace() + "_" + attrId.getPath());
    }

    /**
     * 按指定等级应用或移除某职业的全部属性加成。
     *
     * @param secondary true 时用副职业前缀的修饰符 sourceId，false 时用主职业前缀。
     *                  （由调用方显式传入，不再从 {@code currentSecondary} 推断 ——
     *                  多副职业共存后某职业是否"副职业"需由调用上下文决定）
     */
    private static void applyBonusAtLevel(ServerPlayer player, IProfession prof, int level, boolean add,
                                          boolean secondary) {
        if (level < 1) level = 1;
        for (Map.Entry<Identifier, Integer> entry : prof.getBaseBonusMap().entrySet()) {
            Identifier attrId = entry.getKey();
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(attrId);
            if (attrEntry == null) continue;
            IAttribute attr = player.getData(attrEntry.getSupplier());
            Identifier sourceId = modifierSourceId(attrId, secondary);
            if (add) {
                attr.addModifier(AttributeModifier.of(sourceId, Operation.ADDITION,
                        prof.getBonusAtLevel(attrId, level)));
            } else {
                attr.removeModifier(sourceId);
            }
        }
    }

    /**
     * 重新应用当前主职业加成（先移除再添加）。用于等级变化、登录重建。
     */
    public static void reapplyMainBonuses(ServerPlayer player) {
        ProfessionData data = getData(player);
        IProfession prof = registry.getProfession(data.getProfessionId());
        if (prof == null) return;
        // 移除（用任意等级，remove 只看 sourceId）
        applyBonusAtLevel(player, prof, 1, false, false);
        applyBonusAtLevel(player, prof, data.getProfessionLevel(prof.getId()), true, false);
    }

    /**
     * 重新应用指定副职业的加成（先移除再添加）。用于该副职业等级变化。
     */
    public static void reapplySecondaryBonus(ServerPlayer player, Identifier secondaryId) {
        ProfessionData data = getData(player);
        if (!data.isSecondaryActive(secondaryId)) return;
        IProfession prof = registry.getProfession(secondaryId);
        if (prof == null) return;
        applyBonusAtLevel(player, prof, 1, false, true);
        applyBonusAtLevel(player, prof, data.getProfessionLevel(secondaryId), true, true);
    }

    /**
     * 重新应用所有已激活副职业的加成。用于登录/重生重建（所有修饰符都已重置）。
     */
    public static void reapplySecondaryBonuses(ServerPlayer player) {
        ProfessionData data = getData(player);
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession prof = registry.getProfession(secId);
            if (prof == null) continue;
            applyBonusAtLevel(player, prof, 1, false, true);
            applyBonusAtLevel(player, prof, data.getProfessionLevel(secId), true, true);
        }
    }

    /** 登录/重生时重新应用所有生效加成 */
    public static void reapplyAllBonuses(ServerPlayer player) {
        reapplyMainBonuses(player);
        reapplySecondaryBonuses(player);
    }

    // ====================================================================
    // 行为钩子调度（战斗 + 生命周期）
    // ====================================================================

    /**
     * 注册战斗事件中央调度器到 {@link RPGEventBus}。
     * <p>
     * 战斗事件触发时遍历玩家当前生效的职业（主职业 + 所有已激活副职业），
     * 依次调用对应钩子（{@code onAttack} / {@code onDamaged} / {@code onKill}）。
     */
    private static void registerCombatDispatchers() {
        // Pre：攻击命中前。仅处理玩家作为攻击者的 onAttack
        RPGEventBus.register(RPGDamageEvent.Pre.class, event -> {
            LivingEntity attacker = event.getAttacker();
            if (!(attacker instanceof ServerPlayer player)) return;
            dispatchAttack(player, event.getTarget(), event.getDamage(), event.getAttackType());
        });
        // Post：伤害应用后。区分攻击者（onKill）/ 被攻击者（onDamaged）
        RPGEventBus.register(RPGDamageEvent.Post.class, event -> {
            LivingEntity attacker = event.getAttacker();
            LivingEntity target = event.getTarget();
            if (attacker instanceof ServerPlayer atkPlayer) {
                // 攻击者：致命则触发 onKill，否则无额外钩子（onAttack 已在 Pre 阶段触发）
                if (event.isLethal()) {
                    dispatchKill(atkPlayer, target, event.getDamageDealt(), event.getAttackType());
                }
            }
            if (target instanceof ServerPlayer targetPlayer) {
                dispatchDamaged(targetPlayer, attacker, event.getDamageDealt(), event.getAttackType());
            }
        });
    }

    /**
     * 对玩家当前生效的职业（主 + 已激活副）依次触发 onAttack。
     * 注意：Pre 阶段的 damage 是计算前值，可能随后被公式修改。
     */
    private static void dispatchAttack(ServerPlayer player, LivingEntity target,
                                       int damage, com.rpgcraft.core.attribute.AttackType attackType) {
        ProfessionData data = getData(player);
        // 主职业
        IProfession main = registry.getProfession(data.getProfessionId());
        if (main != null) {
            main.onAttack(buildCombatCtx(player, main, target, damage, attackType, true));
        }
        // 已激活副职业
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = registry.getProfession(secId);
            if (sec != null) {
                sec.onAttack(buildCombatCtx(player, sec, target, damage, attackType, true));
            }
        }
    }

    private static void dispatchDamaged(ServerPlayer player, @Nullable LivingEntity attacker,
                                        int damage, com.rpgcraft.core.attribute.AttackType attackType) {
        ProfessionData data = getData(player);
        IProfession main = registry.getProfession(data.getProfessionId());
        if (main != null) {
            main.onDamaged(buildCombatCtx(player, main, attacker, damage, attackType, false));
        }
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = registry.getProfession(secId);
            if (sec != null) {
                sec.onDamaged(buildCombatCtx(player, sec, attacker, damage, attackType, false));
            }
        }
    }

    private static void dispatchKill(ServerPlayer player, LivingEntity victim,
                                     int damage, com.rpgcraft.core.attribute.AttackType attackType) {
        ProfessionData data = getData(player);
        IProfession main = registry.getProfession(data.getProfessionId());
        if (main != null) {
            main.onKill(buildCombatCtx(player, main, victim, damage, attackType, true));
        }
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = registry.getProfession(secId);
            if (sec != null) {
                sec.onKill(buildCombatCtx(player, sec, victim, damage, attackType, true));
            }
        }
    }

    private static ProfessionCombatContext buildCombatCtx(ServerPlayer player, IProfession prof,
                                                          @Nullable LivingEntity opponent, int damage,
                                                          com.rpgcraft.core.attribute.AttackType attackType,
                                                          boolean isAttacker) {
        return new ProfessionCombatContext(player, prof,
                getData(player).getProfessionLevel(prof.getId()),
                opponent, damage, attackType, isAttacker);
    }

    /**
     * 触发玩家当前生效职业（主 + 已激活副）的 onLogin 钩子。
     * 由 {@link ProfessionLoginEventHandler} 在加成重建后调用。
     */
    public static void notifyLoginHooks(ServerPlayer player) {
        ProfessionData data = getData(player);
        IProfession main = registry.getProfession(data.getProfessionId());
        if (main != null) {
            main.onLogin(new ProfessionContext(player, main, data.getProfessionLevel(main.getId())));
        }
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = registry.getProfession(secId);
            if (sec != null) {
                sec.onLogin(new ProfessionContext(player, sec, data.getProfessionLevel(secId)));
            }
        }
    }

    /**
     * 触发玩家当前生效职业（主 + 已激活副）的 onRespawn 钩子。
     * 由 {@link ProfessionSnapshotContributor} 在重生加成重建后调用。
     */
    public static void notifyRespawnHooks(ServerPlayer player) {
        ProfessionData data = getData(player);
        IProfession main = registry.getProfession(data.getProfessionId());
        if (main != null) {
            main.onRespawn(new ProfessionContext(player, main, data.getProfessionLevel(main.getId())));
        }
        for (Identifier secId : data.getActiveSecondaryProfessions()) {
            IProfession sec = registry.getProfession(secId);
            if (sec != null) {
                sec.onRespawn(new ProfessionContext(player, sec, data.getProfessionLevel(secId)));
            }
        }
    }

    // ====================================================================
    // 兼容旧 API
    // ====================================================================

    /** @deprecated 直接切换，不校验进阶/解锁规则。仅命令调试用。 */
    @Deprecated
    public static void setProfession(ServerPlayer player, Identifier professionId) {
        IProfession prof = registry.getProfession(professionId);
        if (prof == null) return;
        // 主职业只能设主职业类型的职业（副职业不能通过此命令设为主职业）
        if (prof.getType() != IProfession.ProfessionType.PRIMARY) {
            ProfessionMod.LOGGER.warn("setProfession 拒绝将副职业 {} 设为主职业", professionId);
            return;
        }
        ProfessionData data = getData(player);
        // 顺带解锁并设为 1 级（调试场景）
        data.unlock(professionId);
        data.setProfessionLevel(professionId, Math.max(1, data.getProfessionLevel(professionId)));
        switchToMain(player, professionId);
    }

    public static void applyProfessionBonuses(ServerPlayer player) {
        reapplyMainBonuses(player);
    }

    public static void removeProfessionBonuses(ServerPlayer player) {
        ProfessionData data = getData(player);
        IProfession prof = registry.getProfession(data.getProfessionId());
        if (prof != null) applyBonusAtLevel(player, prof, 1, false, false);
    }

    // ====================================================================
    // 网络同步
    // ====================================================================

    public static void syncToClient(ServerPlayer player) {
        ProfessionData data = player.getData(PLAYER_PROFESSION);
        // 1. 轻量职业ID同步（写入客户端 attachment，供角色界面 PlayerInfoPlugin 读取）
        SyncPlayerProfessionPacket.sendToClient(player, data);
        // 2. 完整职业状态同步（写入客户端面板缓存，供职业面板实时刷新）
        com.rpgcraft.core.network.SyncProfessionStatePacket.sendToClient(player, buildStateView(player));
    }

    private static void syncAffectedAttributes(ServerPlayer player,
                                                IProfession oldProf, IProfession newProf) {
        Set<Identifier> affected = new HashSet<>();
        if (oldProf != null) affected.addAll(oldProf.getBaseBonusMap().keySet());
        if (newProf != null) affected.addAll(newProf.getBaseBonusMap().keySet());
        for (Identifier attrId : affected) {
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(attrId);
            if (attrEntry == null) continue;
            EntityAttribute attr = (EntityAttribute) player.getData(attrEntry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(player, attrId, attr);
        }
    }

    // ====================================================================
    // IProfessionSystem 实现
    // ====================================================================

    private static final class ProfessionSystemImpl implements IProfessionSystem {
        @Override public void syncToClient(ServerPlayer p) { ProfessionManager.syncToClient(p); }
        @Override public IProfession getProfession(ServerPlayer p) { return ProfessionManager.getProfession(p); }
        @Override public IProfession getProfessionById(Identifier id) { return registry.getProfession(id); }
        @Override public Collection<IProfession> getAllProfessions() { return registry.getAllProfessions(); }
        @Override public void setProfession(ServerPlayer p, Identifier id) { ProfessionManager.setProfession(p, id); }

        @Override public int getSkillPointPool(ServerPlayer p) { return getData(p).getSkillPointPool(); }
        @Override public int getProfessionLevel(ServerPlayer p, Identifier id) { return getData(p).getProfessionLevel(id); }
        @Override public int getLevelCap() { return LEVEL_CAP; }
        @Override public int getExpForNextLevel(int level) { return ProfessionManager.getExpForNextLevel(level); }
        @Override public void onPlayerLeveledUp(ServerPlayer p, int exp) { ProfessionManager.onPlayerLeveledUp(p, exp); }
        @Override public boolean canInvest(ServerPlayer p, Identifier id) { return ProfessionManager.canInvest(p, id); }
        @Override public boolean investLevel(ServerPlayer p, Identifier id) { return ProfessionManager.investLevel(p, id); }

        @Override public boolean canAdvance(ServerPlayer p, Identifier id) { return ProfessionManager.canAdvance(p, id); }
        @Override public boolean advance(ServerPlayer p, Identifier id) { return ProfessionManager.advance(p, id); }
        @Override public boolean isUnlocked(ServerPlayer p, Identifier id) { return ProfessionManager.isUnlocked(p, id); }
        @Override public Collection<Identifier> getUnlocked(ServerPlayer p) { return ProfessionManager.getUnlocked(p); }

        @Override public boolean canSwitchMain(ServerPlayer p, Identifier id) { return ProfessionManager.canSwitchMain(p, id); }
        @Override public boolean switchMain(ServerPlayer p, Identifier id) { return ProfessionManager.switchMain(p, id); }

        @Override public Set<Identifier> getActiveSecondary(ServerPlayer p) { return ProfessionManager.getActiveSecondary(p); }
        @Override public boolean isSecondaryActive(ServerPlayer p, Identifier id) { return ProfessionManager.isSecondaryActive(p, id); }
        @Override public void setSecondaryActive(ServerPlayer p, Identifier id, boolean a) { ProfessionManager.setSecondaryActive(p, id, a); }
        @Override public boolean unlockSecondary(ServerPlayer p, Identifier id) { return ProfessionManager.unlockSecondary(p, id); }
    }
}
