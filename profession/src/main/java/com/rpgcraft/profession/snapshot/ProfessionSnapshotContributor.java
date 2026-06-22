package com.rpgcraft.profession.snapshot;

import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.ISnapshotContributor;
import com.rpgcraft.profession.ProfessionManager;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 职业快照贡献者
 * <p>
 * 捕获和恢复玩家的完整职业数据（主职业、已激活副职业集合、职业经验池、各职业等级、已解锁集合），
 * 并重新应用职业属性加成（按等级线性计算的 ADDITION 修饰符）。
 * <p>
 * <b>重生时必须重新应用职业加成修饰符</b>：{@link net.neoforged.neoforge.event.player.PlayerEvent.Clone}
 * 会创建全新的玩家实体，所有附件和修饰符都被重置。仅恢复数据不够 ——
 * 职业提供的属性修饰符需要由本贡献者重新添加（按当前职业等级线性计算）。
 * <p>
 * <b>执行顺序依赖</b>：必须在 {@code AttributeSnapshotContributor} 之后注册，
 * 以确保属性基础值已恢复后再叠加职业修饰符。当前由模组加载顺序保证。
 * <p>
 * 两种死亡恢复模式下行为相同：恢复完整职业数据 + 重新应用加成。
 */
public class ProfessionSnapshotContributor implements ISnapshotContributor {

    private static final String CONTRIBUTOR_ID = "rpgcraftprofession:profession";

    /**
     * 完整职业快照数据
     */
    record ProfSnapshot(
            Identifier professionId,
            Set<Identifier> activeSecondaryProfessions,
            int skillPointPool,
            Map<Identifier, Integer> professionLevels,
            Set<Identifier> unlockedProfessions
    ) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public Object captureSnapshot(ServerPlayer player) {
        ProfessionData data = player.getData(ProfessionManager.PLAYER_PROFESSION);
        return new ProfSnapshot(
                data.getProfessionId(),
                new LinkedHashSet<>(data.getActiveSecondaryProfessions()),
                data.getSkillPointPool(),
                new LinkedHashMap<>(data.getProfessionLevels()),
                new LinkedHashSet<>(data.getUnlockedProfessions())
        );
    }

    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        ProfSnapshot snap = (ProfSnapshot) data;
        ProfessionData newData = newPlayer.getData(ProfessionManager.PLAYER_PROFESSION);
        newData.setProfessionId(snap.professionId());
        newData.setSkillPointPool(snap.skillPointPool());
        // 逐项恢复等级与解锁状态
        for (Map.Entry<Identifier, Integer> e : snap.professionLevels().entrySet()) {
            newData.setProfessionLevel(e.getKey(), e.getValue());
        }
        for (Identifier id : snap.unlockedProfessions()) {
            newData.unlock(id);
        }
        // 恢复已激活副职业集合（先清空再逐个设回）
        for (Identifier id : newData.getActiveSecondaryProfessions()) {
            newData.setSecondaryActive(id, false);
        }
        for (Identifier id : snap.activeSecondaryProfessions()) {
            newData.setSecondaryActive(id, true);
        }
        // 重生（PlayerEvent.Clone）创建的是全新实体，职业加成修饰符需重新应用
        ProfessionManager.reapplyAllBonuses(newPlayer);
        // 生命周期钩子：重生（加成重建后触发）
        ProfessionManager.notifyRespawnHooks(newPlayer);
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        ProfessionManager.syncToClient(player);
    }
}
