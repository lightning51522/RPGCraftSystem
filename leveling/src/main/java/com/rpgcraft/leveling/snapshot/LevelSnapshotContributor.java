package com.rpgcraft.leveling.snapshot;

import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.ISnapshotContributor;
import com.rpgcraft.core.level.PlayerLevelData;
import com.rpgcraft.leveling.LevelManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * 等级快照贡献者
 * <p>
 * 捕获和恢复玩家的等级和经验数据。
 */
public class LevelSnapshotContributor implements ISnapshotContributor<LevelSnapshotContributor.LevelData> {

    private static final String CONTRIBUTOR_ID = "rpgcraftcore:level";

    /** 等级快照数据 */
    record LevelData(int level, int experience) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public LevelData captureSnapshot(ServerPlayer player) {
        PlayerLevelData data = player.getData(LevelManager.PLAYER_LEVEL);
        return new LevelData(data.getLevel(), data.getExperience());
    }

    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, LevelData levelData, DeathRestoreMode mode) {
        PlayerLevelData newLevelData = newPlayer.getData(LevelManager.PLAYER_LEVEL);
        newLevelData.setLevel(levelData.level());
        newLevelData.setExperience(levelData.experience());
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        LevelManager.syncToClient(player);
    }
}
