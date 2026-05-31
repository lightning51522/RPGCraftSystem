package com.rpgcraft.core.equipment.api;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.equipment.EquipmentBonus;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 装备属性加成处理器
 * <p>
 * 定义装备加成的计算和应用逻辑。默认实现为
 * {@link com.rpgcraft.core.equipment.DefaultEquipmentHandler}。
 * <p>
 * 其他模组可以替换此实现来提供自定义的装备加成机制，
 * 通过 {@code EquipmentManager.setHandler()} 注入。
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
     * 从死亡快照中剥离死亡时的装备加成得到基础值，
     * 再根据玩家当前身上实际装备重新计算总加成并应用。
     * 适用于装备在死亡时掉落的场景。
     * <p>
     * 该方法同时会更新装备加成追踪附件（等效于调用 {@link #restoreBonusTracking}），
     * 并将所有属性同步到客户端。
     *
     * @param player                重生后的新玩家实体
     * @param deathSnapshot         死亡时的属性快照
     * @param deathEquipmentBonuses 死亡时的装备加成映射（字符串键，来自追踪附件）
     */
    void rescanAndApplyAttributes(ServerPlayer player,
                                   AttributeSnapshot deathSnapshot,
                                   Map<String, EquipmentBonus> deathEquipmentBonuses);
}
