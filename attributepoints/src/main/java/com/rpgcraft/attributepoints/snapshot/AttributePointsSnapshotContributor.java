package com.rpgcraft.attributepoints.snapshot;

import com.rpgcraft.attributepoints.AttributePointsManager;
import com.rpgcraft.core.attributepoint.PlayerAttributePoints;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.ISnapshotContributor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 属性点快照贡献者
 * <p>
 * 捕获和恢复玩家的属性点数据（可分配点数 + 各属性已分配点数），并在恢复后重新应用
 * 所有属性点修饰符。
 * <p>
 * <b>重生时必须重新应用属性点修饰符</b>：{@link net.neoforged.neoforge.event.player.PlayerEvent.Clone}
 * 创建全新玩家实体，修饰符全部丢失。恢复 allocations 数据后必须重建对应的 ADDITION 修饰符，
 * 否则玩家会丢失属性点加成。这与职业模块的 {@code applyProfessionBonuses} 是对称设计。
 * <p>
 * <b>执行顺序依赖</b>：本贡献者必须在 {@code AttributeSnapshotContributor} 之后注册/执行，
 * 以确保属性基础值已恢复后再叠加属性点修饰符。当前由模组加载顺序保证（attributes 模块
 * 在 attributepoints 模块之前注册贡献者），勿随意调整注册顺序。
 * <p>
 * 两种死亡恢复模式下行为相同：恢复 allocations + 重建修饰符。属性点是玩家拥有的持久数据，
 * 不参与"死亡装备加成剥离"语义，因此忽略 {@link DeathRestoreMode}（与 leveling 模块一致）。
 */
public class AttributePointsSnapshotContributor implements ISnapshotContributor {

    private static final String CONTRIBUTOR_ID = "rpgcraftattributepoints:attribute_points";

    /**
     * 属性点快照数据
     *
     * @param available   可分配点数
     * @param allocations 属性 ID → 已分配点数
     */
    record PointsData(int available, Map<Identifier, Integer> allocations) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public Object captureSnapshot(ServerPlayer player) {
        PlayerAttributePoints data = player.getData(AttributePointsManager.PLAYER_ATTRIBUTE_POINTS);
        // 复制 allocations 到新 Map（原数据是不可变视图或会被回收）
        return new PointsData(
                data.getAvailablePoints(),
                new LinkedHashMap<>(data.getAllocations())
        );
    }

    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        PointsData pointsData = (PointsData) data;
        PlayerAttributePoints newData = newPlayer.getData(AttributePointsManager.PLAYER_ATTRIBUTE_POINTS);
        newData.setAvailablePoints(pointsData.available());
        // 重建 allocations
        newData.clearAllocations();
        for (Map.Entry<Identifier, Integer> entry : pointsData.allocations().entrySet()) {
            newData.setAllocation(entry.getKey(), entry.getValue());
        }
        // 重新应用所有属性点修饰符（修饰符不跨 Clone 保留）
        AttributePointsManager.reapplyAllModifiers(newPlayer);
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        AttributePointsManager.syncToClient(player);
    }
}
