package com.rpgcraft.skills.snapshot;

import com.rpgcraft.core.skill.PlayerSkillData;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.ISnapshotContributor;
import com.rpgcraft.skills.SkillsManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 技能快照贡献者
 * <p>
 * 捕获和恢复玩家的技能数据（冷却到期 tick + 已学技能），在死亡/重生时保留进度。
 * <p>
 * <b>MVP 设计</b>：技能冷却与已学技能按 {@code SNAPSHOT} 模式完全保留（死亡不掉冷却进度），
 * 与 leveling/attributepoints 的"持久玩家数据"语义一致。两种死亡恢复模式下行为相同。
 * <p>
 * <b>执行顺序</b>：无严格顺序依赖（不依赖其他模块的属性基础值），但仍遵循工程约定
 * 在模组入口构造函数中尽早注册。
 */
public class SkillSnapshotContributor implements ISnapshotContributor {

    private static final String CONTRIBUTOR_ID = "rpgcraftskills:skills";

    /**
     * 技能快照数据
     *
     * @param cooldowns 技能 ID → 冷却到期 tick
     * @param learned   已学习技能 ID 列表
     */
    record SkillSnapshotData(Map<Identifier, Long> cooldowns, List<Identifier> learned) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public Object captureSnapshot(ServerPlayer player) {
        PlayerSkillData data = player.getData(SkillsManager.PLAYER_SKILLS);
        return new SkillSnapshotData(
                new LinkedHashMap<>(data.getCooldowns()),
                List.copyOf(data.getLearned())
        );
    }

    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        SkillSnapshotData snapshot = (SkillSnapshotData) data;
        PlayerSkillData newData = newPlayer.getData(SkillsManager.PLAYER_SKILLS);
        // 重建冷却
        newData.clearCooldowns();
        for (Map.Entry<Identifier, Long> entry : snapshot.cooldowns().entrySet()) {
            newData.startCooldown(entry.getKey(), entry.getValue());
        }
        // 重建已学集合（先 forget 全部避免重复，实际 LinkedHashSet 去重也可）
        for (Identifier id : snapshot.learned()) {
            newData.learn(id);
        }
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        SkillsManager.syncToClient(player);
    }
}
