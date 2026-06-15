package com.rpgcraft.core.equipment.api;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.equipment.EquipmentBonus;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 装备属性加成处理器
 * <p>
 * 定义装备加成的计算和应用逻辑。
 * <p>
 * 其他模组可以替换此实现来提供自定义的装备加成机制，
 * 通过 {@link com.rpgcraft.core.registry.RPGSystems} 注册的 {@link com.rpgcraft.core.registry.IEquipmentSystem}
 * 注入自定义处理器。
 */
public interface IEquipmentHandler {

    /**
     * 计算玩家当前所有装备的总加成
     *
     * @param player 目标玩家
     * @return 属性ID → 总加成值的映射
     */
    Map<Identifier, EquipmentBonus> calculateTotalBonus(ServerPlayer player);

    /**
     * 处理装备变化事件
     * <p>
     * 计算新旧加成差值并应用到玩家属性。
     *
     * @param player 装备发生变化的玩家
     */
    void onEquipmentChange(ServerPlayer player);

    /**
     * 在重生或登录后恢复装备加成追踪数据
     *
     * @param player 目标玩家
     */
    void restoreBonusTracking(ServerPlayer player);

    /**
     * 重扫模式下根据当前装备重新计算并应用所有属性
     * <p>
     * <b>已废弃（默认空实现）</b>：RESCAN 语义现由快照贡献者协作实现 ——
     * {@code AttributeSnapshotContributor} 恢复纯基础值，{@code EquipmentSnapshotContributor}
     * 通过 {@link #restoreBonusTracking} 根据重生后装备重新添加修饰符。本接口方法
     * 保留为 {@code default} 空实现仅为向后兼容（避免破坏实现此接口的第三方处理器），
     * 不再有默认实现调用它。
     *
     * @param player                重生后的新玩家实体（未使用）
     * @param deathSnapshot         死亡时的属性快照（未使用）
     * @param deathEquipmentBonuses 死亡时的装备加成映射（未使用）
     */
    default void rescanAndApplyAttributes(ServerPlayer player,
                                          AttributeSnapshot deathSnapshot,
                                          Map<String, EquipmentBonus> deathEquipmentBonuses) {
        // 默认空实现：RESCAN 语义由 restoreBonusTracking + AttributeSnapshotContributor 协作完成
    }
}
