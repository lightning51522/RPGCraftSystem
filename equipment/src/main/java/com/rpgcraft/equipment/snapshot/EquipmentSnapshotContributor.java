package com.rpgcraft.equipment.snapshot;

import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.ISnapshotContributor;
import com.rpgcraft.equipment.EquipmentData;
import com.rpgcraft.equipment.EquipmentManager;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 装备快照贡献者
 * <p>
 * 捕获和恢复装备加成追踪数据。
 * 快照模式下恢复追踪映射；重扫模式下根据当前装备重新添加修饰符。
 */
public class EquipmentSnapshotContributor implements ISnapshotContributor {

    private static final String CONTRIBUTOR_ID = "rpgcraftequipment:equipment";

    /**
     * 单条装备加成快照数据
     *
     * @param attrId 属性 ID 字符串
     * @param value  加成数值
     */
    record EquipBonusData(String attrId, int value) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public Object captureSnapshot(ServerPlayer player) {
        Map<String, EquipmentBonus> bonusMap = player.getData(EquipmentData.EQUIPMENT_BONUS.get());
        List<EquipBonusData> list = new ArrayList<>();
        for (Map.Entry<String, EquipmentBonus> entry : bonusMap.entrySet()) {
            list.add(new EquipBonusData(entry.getKey(), entry.getValue().value()));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        if (mode == DeathRestoreMode.RESCAN) {
            // 重扫模式：根据当前装备重新计算并应用修饰符
            EquipmentManager.getHandler().restoreBonusTracking(newPlayer);
        } else {
            // 快照模式：恢复装备追踪数据（属性值已由 AttributeSnapshotContributor 恢复，含加成）
            EquipmentManager.getHandler().restoreBonusTracking(newPlayer);
        }
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        // 装备变化引起的属性同步由 AttributeSnapshotContributor 统一处理
    }
}
