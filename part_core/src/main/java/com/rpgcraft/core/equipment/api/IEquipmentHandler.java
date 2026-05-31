package com.rpgcraft.core.equipment.api;

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
}
