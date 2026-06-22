package com.rpgcraft.core.registry;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * 属性点系统接口
 * <p>
 * 由属性点模块（{@code rpgcraftattributepoints}）注册实现，提供玩家属性点的查询与分配能力。
 * 供其他模块（如 UI、命令系统）查询和操作玩家的属性点。
 * <p>
 * <b>属性点语义</b>：每个属性点对应 1 点 {@code ADDITION} 属性修饰符，应用到对应属性上。
 * 可分配属性 = 所有 {@code shouldResetOnRespawn() == false} 的注册属性
 * （即排除 life/skill_point 等资源型属性）。
 *
 * @see RPGSystems#registerAttributePointSystem(IAttributePointSystem)
 * @see RPGSystems#getAttributePointSystem()
 */
public interface IAttributePointSystem {

    /**
     * 获取玩家当前可分配的属性点数
     *
     * @param player 服务端玩家
     * @return 可分配点数（≥ 0）
     */
    int getAvailablePoints(ServerPlayer player);

    /**
     * 获取玩家在某属性上已分配的点数
     *
     * @param player 服务端玩家
     * @param attrId 属性标识符
     * @return 已分配点数（未分配过返回 0）
     */
    int getAllocatedPoints(ServerPlayer player, Identifier attrId);

    /**
     * 获取玩家所有属性的分配情况
     *
     * @param player 服务端玩家
     * @return 属性 ID → 已分配点数 的映射（不可变视图）
     */
    Map<Identifier, Integer> getAllAllocations(ServerPlayer player);

    /**
     * 分配属性点到指定属性（服务端校验 + 应用修饰符 + 同步）
     * <p>
     * 校验：可分配点数充足、目标属性是可分配属性（非资源型）。
     *
     * @param player 服务端玩家
     * @param attrId 目标属性标识符
     * @param points 要分配的点数（必须 &gt; 0）
     * @return {@code true} 分配成功；{@code false} 校验失败（点数不足或属性不可分配）
     */
    boolean allocate(ServerPlayer player, Identifier attrId, int points);

    /**
     * 从指定属性回收已分配的点数（服务端校验 + 应用修饰符 + 同步）
     * <p>
     * 校验：目标属性已分配点数 ≥ points、目标属性是可分配属性。
     * 回收的点数返还到可分配池。
     *
     * @param player 服务端玩家
     * @param attrId 目标属性标识符
     * @param points 要回收的点数（必须 &gt; 0）
     * @return {@code true} 回收成功；{@code false} 校验失败（已分配不足或属性不可分配）
     */
    boolean deallocate(ServerPlayer player, Identifier attrId, int points);

    /**
     * 授予玩家可分配点数（管理员命令或升级事件调用）
     *
     * @param player 服务端玩家
     * @param points 授予的点数（必须 &gt; 0）
     */
    void grantPoints(ServerPlayer player, int points);

    /**
     * 重置玩家全部分配（退还所有已分配点数为可分配，移除对应修饰符）
     *
     * @param player 服务端玩家
     */
    void reset(ServerPlayer player);

    /**
     * 同步属性点数据到客户端
     *
     * @param player 服务端玩家
     */
    void syncToClient(ServerPlayer player);
}
