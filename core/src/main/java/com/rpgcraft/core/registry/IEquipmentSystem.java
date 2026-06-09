package com.rpgcraft.core.registry;

import com.rpgcraft.core.equipment.api.IEquipmentRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * 装备系统接口
 * <p>
 * 由装备模块注册实现，提供装备加成恢复和查询能力。
 * 供其他模块（如快照恢复、登录同步、客户端 Tooltip 显示）访问装备数据。
 *
 * @see RPGSystems#registerEquipmentSystem(IEquipmentSystem)
 * @see RPGSystems#getEquipmentSystem()
 */
public interface IEquipmentSystem {

    /**
     * 从当前装备恢复加成追踪数据
     * <p>
     * 用于玩家登录、重生等需要重建装备修饰符追踪的场景。
     *
     * @param player 服务端玩家
     */
    void restoreBonusTracking(ServerPlayer player);

    /**
     * 获取装备注册中心
     * <p>
     * 用于客户端 Tooltip 渲染等需要查询装备加成数据的场景。
     *
     * @return 装备注册中心实例
     */
    IEquipmentRegistry getRegistry();

    /**
     * 获取装备配置文件资源定位符
     * <p>
     * 用于客户端资源重载监听器加载装备配置。
     *
     * @return 配置文件标识符
     */
    Identifier getConfigId();
}
