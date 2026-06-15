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
 * <p>
 * <b>两种死亡恢复模式下的行为说明：</b>
 * <ul>
 *   <li>{@code SNAPSHOT} 模式：{@code AttributeSnapshotContributor} 恢复死亡时的最终属性值
 *       （含装备加成），本贡献者负责根据重生后的实际装备重建追踪附件与修饰符。
 *       若重生后装备未变（如 keepInventory），修饰符与死亡时一致；若装备掉落，修饰符
 *       会少加成 —— 但属性最终值仍按死亡快照保留，因此属性条不会因装备掉落而下跌。</li>
 *   <li>{@code RESCAN} 模式：{@code AttributeSnapshotContributor} 仅恢复<b>纯基础值</b>
 *       （剥离所有装备加成），本贡献者根据重生后的实际装备重新添加修饰符。此时若装备
 *       已掉落，对应加成确实丢失 —— 这是 RESCAN 模式的设计语义。</li>
 * </ul>
 * 因此无论哪种模式，本贡献者的工作都是「根据重生后实际装备重扫修饰符」，由
 * {@code restoreBonusTracking} 统一完成。这就是两分支代码相同的原因 —— 不是 bug，
 * 而是装备相关 RESCAN 语义已被拆分为「基础值恢复」（属性贡献者）+「修饰符重扫」（本贡献者）
 * 两个协作环节。
 * <p>
 * <b>客户端同步</b>：本贡献者的 {@link #syncToClient} 是空操作 —— 装备重扫引起的属性
 * 变化由 {@code AttributeSnapshotContributor.syncToClient} 统一同步全量属性到客户端。
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

    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        // 无论 SNAPSHOT 还是 RESCAN 模式，装备相关恢复都由 restoreBonusTracking 统一完成：
        // 根据重生后玩家身上实际装备重新计算总加成，移除旧修饰符后添加新修饰符。
        // 两种模式在「属性值」上的差异由 AttributeSnapshotContributor 处理
        // （SNAPSHOT 恢复最终值含加成；RESCAN 仅恢复基础值不含加成）。
        // 注：data（死亡时的装备加成快照）当前未使用，保留以备未来 RESCAN 增强场景。
        EquipmentManager.getHandler().restoreBonusTracking(newPlayer);
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        // 装备变化引起的属性同步由 AttributeSnapshotContributor 统一处理
    }
}
