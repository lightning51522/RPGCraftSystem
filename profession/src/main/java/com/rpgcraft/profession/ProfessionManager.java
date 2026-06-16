package com.rpgcraft.profession;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeModifier;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.network.ProfessionStateAssembler;
import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.registry.IProfessionSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.profession.api.IProfessionProvider;
import com.rpgcraft.profession.network.SyncPlayerProfessionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
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

    /** 全局职业等级上限 */
    public static final int LEVEL_CAP = 20;

    private static ProfessionRegistry registry;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    // ====================================================================
    // 职业标识符常量
    // ====================================================================

    public static final Identifier COMMONER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "commoner");
    public static final Identifier WARRIOR_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "warrior");
    public static final Identifier ARCHER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "archer");
    public static final Identifier BERSERKER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "berserker");
    public static final Identifier MARKSMAN_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "marksman");

    // ====================================================================
    // AttachmentType Supplier
    // ====================================================================

    public static Supplier<AttachmentType<ProfessionData>> PLAYER_PROFESSION;

    /**
     * 初始化职业模块
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

        // 注册内置职业（注册顺序决定 UI 树渲染顺序）
        registry.register(new CommonerProfession());
        registry.register(new WarriorProfession());
        registry.register(new BerserkerProfession());
        registry.register(new ArcherProfession());
        registry.register(new MarksmanProfession());

        // SPI：发现子模组注册的自定义职业
        for (IProfessionProvider provider : ServiceLoader.load(IProfessionProvider.class)) {
            provider.registerProfessions(registry);
        }

        RPGSystems.registerPlayerProfessionAttachment(PLAYER_PROFESSION);

        RPGSystems.registerProfessionSystem(new ProfessionSystemImpl());

        // 注册职业状态组装器（供 core 的 RequestProfessionStatePacket 组装完整状态推送给客户端）
        ProfessionStateAssembler.register(ProfessionManager::buildStateView);
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
                    prof.getPrerequisite(), prof.isAdvanced()));
        }
        return new com.rpgcraft.core.ui.ProfessionStateCache.ProfessionStateView(
                data.getSkillPointPool(),
                data.getProfessionId(),
                data.getSecondaryProfessionId(),
                data.isSecondaryActive(),
                new java.util.LinkedHashMap<>(data.getProfessionLevels()),
                new java.util.LinkedHashSet<>(data.getUnlockedProfessions()),
                java.util.Collections.unmodifiableList(nodes)
        );
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

    /** 职业经验表：从 level 升到 level+1 所需经验，公式 round(50 × L^1.5)，L=1..19 */
    private static final int[] EXP_TABLE = generateExpTable();

    private static int[] generateExpTable() {
        int[] table = new int[LEVEL_CAP - 1]; // 索引 0 对应 1→2
        for (int l = 1; l < LEVEL_CAP; l++) {
            table[l - 1] = (int) Math.round(50 * Math.pow(l, 1.5));
        }
        return table;
    }

    public static int getExpForNextLevel(int level) {
        if (level < 1 || level >= LEVEL_CAP) return Integer.MAX_VALUE;
        return EXP_TABLE[level - 1];
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
        int level = data.getProfessionLevel(professionId);
        if (level <= 0 || level >= LEVEL_CAP) return false;
        return data.getSkillPointPool() >= getExpForNextLevel(level);
    }

    public static boolean investLevel(ServerPlayer player, Identifier professionId) {
        if (!canInvest(player, professionId)) return false;
        ProfessionData data = getData(player);
        int level = data.getProfessionLevel(professionId);
        int cost = getExpForNextLevel(level);
        data.addSkillPoints(-cost);
        data.setProfessionLevel(professionId, level + 1);

        // 若投入的是当前主职业或激活的副职业，重算加成
        if (professionId.equals(data.getProfessionId())) {
            reapplyMainBonuses(player);
        } else if (data.isSecondaryActive() && professionId.equals(data.getSecondaryProfessionId())) {
            reapplySecondaryBonuses(player);
        }
        syncToClient(player);
        return true;
    }

    // ====================================================================
    // 进阶
    // ====================================================================

    public static boolean canAdvance(ServerPlayer player, Identifier professionId) {
        IProfession target = registry.getProfession(professionId);
        if (target == null || target.getPrerequisite() == null) return false;
        ProfessionData data = getData(player);
        if (data.isUnlocked(professionId)) return false; // 已进阶
        Identifier prereq = target.getPrerequisite();
        if (!data.isUnlocked(prereq)) return false;
        return data.getProfessionLevel(prereq) >= LEVEL_CAP;
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
     * 规则（见 ProfessionConfig.allow_downgrade_switch）：
     * <ul>
     *   <li>目标必须已解锁</li>
     *   <li>目标不能是当前主职业本身</li>
     *   <li>从进阶职业切回其基础职业：受 allow_downgrade_switch 控制（默认禁止）</li>
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

        // 目标是当前职业的基础职业 → 受切回开关控制
        boolean targetIsPrereqOfCurrent = professionId.equals(current.getPrerequisite());
        if (targetIsPrereqOfCurrent && !ProfessionConfig.isAllowDowngradeSwitch()) {
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
        if (oldProf != null) applyBonusAtLevel(player, oldProf, data.getProfessionLevel(oldProf.getId()), false);
        data.setProfessionId(professionId);
        if (newProf != null) applyBonusAtLevel(player, newProf, data.getProfessionLevel(professionId), true);
        syncToClient(player);
        if (oldProf != null && newProf != null) syncAffectedAttributes(player, oldProf, newProf);
    }

    // ====================================================================
    // 副职业
    // ====================================================================

    public static Identifier getSecondary(ServerPlayer player) {
        return getData(player).getSecondaryProfessionId();
    }

    public static void setSecondary(ServerPlayer player, Identifier professionId) {
        ProfessionData data = getData(player);
        // 清除旧副职业加成
        Identifier oldSec = data.getSecondaryProfessionId();
        if (oldSec != null && data.isSecondaryActive()) {
            IProfession oldProf = registry.getProfession(oldSec);
            if (oldProf != null) applyBonusAtLevel(player, oldProf, data.getProfessionLevel(oldSec), false);
        }
        if (professionId == null) {
            data.setSecondaryProfessionId(null);
        } else {
            // 副职业必须已解锁，且不能等于当前主职业
            if (!data.isUnlocked(professionId) || professionId.equals(data.getProfessionId())) {
                return;
            }
            data.setSecondaryProfessionId(professionId);
            // 新副职业若开关开启则立即应用
            if (data.isSecondaryActive()) {
                IProfession newProf = registry.getProfession(professionId);
                if (newProf != null) applyBonusAtLevel(player, newProf, data.getProfessionLevel(professionId), true);
            }
        }
        syncToClient(player);
    }

    public static boolean isSecondaryActive(ServerPlayer player) {
        return getData(player).isSecondaryActive();
    }

    public static void setSecondaryActive(ServerPlayer player, boolean active) {
        ProfessionData data = getData(player);
        if (data.isSecondaryActive() == active) return;
        data.setSecondaryActive(active);
        Identifier sec = data.getSecondaryProfessionId();
        if (sec != null) {
            IProfession secProf = registry.getProfession(sec);
            if (secProf != null) {
                applyBonusAtLevel(player, secProf, data.getProfessionLevel(sec), active);
            }
        }
        syncToClient(player);
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
     * 按指定等级应用或移除某职业的全部属性加成
     */
    private static void applyBonusAtLevel(ServerPlayer player, IProfession prof, int level, boolean add) {
        if (level < 1) level = 1;
        boolean secondary = !prof.getId().equals(getData(player).getProfessionId())
                && prof.getId().equals(getData(player).getSecondaryProfessionId());
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
        applyBonusAtLevel(player, prof, 1, false);
        applyBonusAtLevel(player, prof, data.getProfessionLevel(prof.getId()), true);
    }

    /**
     * 重新应用副职业加成（仅当开关开启）。
     */
    public static void reapplySecondaryBonuses(ServerPlayer player) {
        ProfessionData data = getData(player);
        if (!data.isSecondaryActive()) return;
        Identifier sec = data.getSecondaryProfessionId();
        if (sec == null) return;
        IProfession prof = registry.getProfession(sec);
        if (prof == null) return;
        applyBonusAtLevel(player, prof, 1, false);
        applyBonusAtLevel(player, prof, data.getProfessionLevel(sec), true);
    }

    /** 登录/重生时重新应用所有生效加成 */
    public static void reapplyAllBonuses(ServerPlayer player) {
        reapplyMainBonuses(player);
        reapplySecondaryBonuses(player);
    }

    // ====================================================================
    // 兼容旧 API
    // ====================================================================

    /** @deprecated 直接切换，不校验进阶/解锁规则。仅命令调试用。 */
    @Deprecated
    public static void setProfession(ServerPlayer player, Identifier professionId) {
        IProfession prof = registry.getProfession(professionId);
        if (prof == null) return;
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
        if (prof != null) applyBonusAtLevel(player, prof, 1, false);
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

        @Override public Identifier getSecondary(ServerPlayer p) { return ProfessionManager.getSecondary(p); }
        @Override public void setSecondary(ServerPlayer p, Identifier id) { ProfessionManager.setSecondary(p, id); }
        @Override public boolean isSecondaryActive(ServerPlayer p) { return ProfessionManager.isSecondaryActive(p); }
        @Override public void setSecondaryActive(ServerPlayer p, boolean a) { ProfessionManager.setSecondaryActive(p, a); }
    }
}
