package com.rpgcraft.core.registry;

import com.rpgcraft.core.profession.api.IProfession;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * 职业系统接口
 * <p>
 * 由职业模块注册实现，提供职业查询、切换、职业经验/等级、进阶、副职业、同步等能力。
 * 供其他模块（如命令系统、客户端渲染、快照恢复、登录同步）通过 {@link RPGSystems} 访问。
 *
 * @see RPGSystems#registerProfessionSystem(IProfessionSystem)
 * @see RPGSystems#getProfessionSystem()
 */
public interface IProfessionSystem {

    // ====================================================================
    // 基础查询
    // ====================================================================

    /**
     * 同步职业数据到客户端
     */
    void syncToClient(ServerPlayer player);

    /**
     * 获取玩家的当前主职业
     */
    IProfession getProfession(ServerPlayer player);

    /**
     * 根据 ID 查询职业（不存在返回 null）
     */
    IProfession getProfessionById(Identifier id);

    /**
     * 获取所有已注册的职业
     */
    Collection<IProfession> getAllProfessions();

    /**
     * 设置玩家的主职业（直接切换，GM 调试用；不校验进阶/解锁规则）
     */
    void setProfession(ServerPlayer player, Identifier professionId);

    // ====================================================================
    // 职业经验与等级
    // ====================================================================

    /**
     * 获取可分配职业经验池
     */
    int getSkillPointPool(ServerPlayer player);

    /**
     * 获取玩家在某职业上的等级（未解锁/未记录返回 0）
     */
    int getProfessionLevel(ServerPlayer player, Identifier professionId);

    /**
     * 全局职业等级上限
     */
    int getLevelCap();

    /**
     * 升某一级所需的职业经验（从 level 升到 level+1）
     *
     * @param level 当前等级（1..maxLevel-1）
     */
    int getExpForNextLevel(int level);

    /**
     * 玩家获得经验时把等量经验加进职业经验池（由 {@code PlayerExpGainEvent} 监听器调用，
     * 使职业经验与等级经验同步获得，不论是否升级）
     *
     * @param player    玩家
     * @param expGained 本次经验增量
     */
    void onPlayerLeveledUp(ServerPlayer player, int expGained);

    /**
     * 是否可以向某职业投入一级（已解锁、未满级、池足够）
     */
    boolean canInvest(ServerPlayer player, Identifier professionId);

    /**
     * 向某职业投入一级（消耗恰好升一级所需的经验）。失败返回 false。
     */
    boolean investLevel(ServerPlayer player, Identifier professionId);

    // ====================================================================
    // 进阶
    // ====================================================================

    /**
     * 是否可以进阶到某职业（目标有前置、前置已解锁且满级、目标尚未解锁）
     */
    boolean canAdvance(ServerPlayer player, Identifier professionId);

    /**
     * 进阶到某职业。失败返回 false。
     */
    boolean advance(ServerPlayer player, Identifier professionId);

    /**
     * 玩家是否已解锁某职业
     */
    boolean isUnlocked(ServerPlayer player, Identifier professionId);

    /**
     * 获取已解锁职业集合
     */
    Collection<Identifier> getUnlocked(ServerPlayer player);

    // ====================================================================
    // 主职业切换（受进阶切换规则约束）
    // ====================================================================

    /**
     * 是否可以切换当前主职业到目标职业
     */
    boolean canSwitchMain(ServerPlayer player, Identifier professionId);

    /**
     * 切换当前主职业。失败返回 false。
     */
    boolean switchMain(ServerPlayer player, Identifier professionId);

    // ====================================================================
    // 副职业（仅提供被动加成，不可作为当前主职业）
    // ====================================================================

    /**
     * 获取当前副职业 ID（null 表示无）
     */
    @Nullable
    Identifier getSecondary(ServerPlayer player);

    /**
     * 设置副职业（需已解锁；传 null 清除副职业）
     */
    void setSecondary(ServerPlayer player, @Nullable Identifier professionId);

    /**
     * 副职业加成开关
     */
    boolean isSecondaryActive(ServerPlayer player);

    /**
     * 设置副职业加成开关
     */
    void setSecondaryActive(ServerPlayer player, boolean active);
}
