package com.rpgcraft.core.registry;

import com.google.gson.JsonObject;
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

    /**
     * 获取稀有度宝石锻造配置文件资源定位符
     * <p>
     * 客户端镜像加载用：铁砧锻造预览（{@code AnvilUpdateEvent}）在客户端也触发，需读取 gemCost 决定
     * 是否展示预览，但服务端 reload 监听器不在客户端触发，故客户端需镜像加载同一配置。
     *
     * @return 稀有度宝石锻造配置标识符；无该功能时返回 {@code null}
     */
    Identifier getGemstoneConfigId();

    /**
     * 应用稀有度宝石锻造配置（镜像加载入口，客户端调用）
     * <p>
     * 由客户端资源重载监听器在加载完 {@link #getGemstoneConfigId()} 指向的 JSON 后调用，
     * 将解析结果写入装备模块的配置状态。
     *
     * @param json 已解析的稀有度宝石锻造配置 JSON
     */
    void applyGemstoneConfig(JsonObject json);
}
