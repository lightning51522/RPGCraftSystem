package com.rpgcraft.profession.snapshot;

import com.rpgcraft.core.profession.ProfessionData;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.ISnapshotContributor;
import com.rpgcraft.profession.ProfessionManager;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 职业快照贡献者
 * <p>
 * 捕获和恢复玩家的职业标识符，并重新应用职业属性加成（ADDITION 修饰符）。
 * <p>
 * <b>重生时必须重新应用职业加成修饰符</b>：{@link net.neoforged.neoforge.event.player.PlayerEvent.Clone}
 * 会创建全新的玩家实体，所有附件和修饰符都被重置。仅恢复职业 ID 不够 ——
 * 职业提供的属性修饰符（如战士的力量 +5）需要由本贡献者重新添加。
 * 这与装备模块的 {@code restoreBonusTracking}（重生后重扫装备修饰符）是对称设计。
 * <p>
 * <b>执行顺序依赖</b>：本贡献者必须在 {@code AttributeSnapshotContributor} 之后注册，
 * 以确保属性基础值已恢复后再叠加职业修饰符。当前由模组加载顺序保证（attributes
 * 模块在 profession 模块之前注册贡献者），勿随意调整注册顺序。
 * <p>
 * 两种死亡恢复模式下行为相同：恢复职业 ID + 重新应用加成。
 */
public class ProfessionSnapshotContributor implements ISnapshotContributor {

    private static final String CONTRIBUTOR_ID = "rpgcraftprofession:profession";

    /**
     * 职业快照数据
     *
     * @param professionId 主职业标识符
     */
    record ProfData(Identifier professionId) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public Object captureSnapshot(ServerPlayer player) {
        ProfessionData data = player.getData(ProfessionManager.PLAYER_PROFESSION);
        return new ProfData(data.getProfessionId());
    }

    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        ProfData profData = (ProfData) data;
        ProfessionData newData = newPlayer.getData(ProfessionManager.PLAYER_PROFESSION);
        newData.setProfessionId(profData.professionId());
        // 重生（PlayerEvent.Clone）创建的是全新实体，职业加成修饰符需重新应用。
        // 必须在 setProfessionId 之后调用，因为 applyProfessionBonuses 从附件读取职业 ID。
        ProfessionManager.applyProfessionBonuses(newPlayer);
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        ProfessionManager.syncToClient(player);
    }
}
