package com.rpgcraft.core.snapshot;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 属性快照贡献者
 * <p>
 * 捕获和恢复玩家的所有 RPG 属性数据（包括基础值和管线计算后的最终值）。
 * 快照模式下直接恢复最终值；重扫模式下只恢复基础值。
 */
public class AttributeSnapshotContributor implements ISnapshotContributor {

    private static final String CONTRIBUTOR_ID = "rpgcraftcore:attributes";

    /**
     * 属性快照数据
     *
     * @param attrId     属性标识符
     * @param current    管线计算后的当前值
     * @param max        管线计算后的上限值
     * @param baseCurrent 基础当前值
     * @param baseMax    基础上限值
     */
    record AttrData(Identifier attrId, int current, int max, int baseCurrent, int baseMax) {}

    @Override
    public String getContributorId() {
        return CONTRIBUTOR_ID;
    }

    @Override
    public Object captureSnapshot(ServerPlayer player) {
        Map<Identifier, AttrData> snapshot = new LinkedHashMap<>();
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            IAttribute attr = player.getData(entry.getSupplier());
            snapshot.put(entry.getId(), new AttrData(
                    entry.getId(),
                    attr.getValue(),
                    attr.getMaxValue(),
                    attr.getBaseValue(),
                    attr.getBaseMaxValue()
            ));
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void restoreSnapshot(ServerPlayer newPlayer, Object data, DeathRestoreMode mode) {
        Map<Identifier, AttrData> snapshot = (Map<Identifier, AttrData>) data;

        for (Map.Entry<Identifier, AttrData> entry : snapshot.entrySet()) {
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(entry.getKey());
            if (attrEntry == null) continue;

            AttrData attrData = entry.getValue();
            IAttribute attr = newPlayer.getData(attrEntry.getSupplier());

            if (mode == DeathRestoreMode.SNAPSHOT) {
                // 快照模式：直接恢复死亡时的最终值
                attr.setMaxValue(attrData.max());
                if (attrEntry.shouldResetOnRespawn()) {
                    attr.fillMax();
                } else {
                    attr.setValue(attrData.current());
                }
            } else {
                // 重扫模式：只恢复基础值，装备修饰符由 EquipmentSnapshotContributor 重新添加
                attr.setBaseMaxValue(attrData.baseMax());
                attr.setBaseValue(attrData.baseCurrent());
            }
        }
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            EntityAttribute attr = (EntityAttribute) player.getData(entry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(player, entry.getId(), attr);
        }
        AttributeManager.syncVanillaHealth(player);
    }
}
