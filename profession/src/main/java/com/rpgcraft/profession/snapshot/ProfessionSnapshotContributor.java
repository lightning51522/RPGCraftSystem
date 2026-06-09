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
 * 捕获和恢复玩家的职业标识符。
 * 快照模式下恢复职业 ID；重扫模式下同样恢复 ID（职业不依赖装备扫描）。
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
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        ProfessionManager.syncToClient(player);
    }
}
