package com.rpgcraft.core.equipment.api;

import com.rpgcraft.core.equipment.EquipmentBonus;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * 装备加成贡献者 SPI
 * <p>
 * 允许外部模块（如 {@code gemstone}）为一件装备<b>追加额外的属性加成</b>，而无需修改
 * 装备模块自身的 {@code DefaultEquipmentHandler.calculateTotalBonus}。这是装备加成计算的
 * 扩展点：装备模块在为每件装备算完基础加成后，调用
 * {@link EquipmentBonusCoordinator#collectAll(ItemStack)} 聚合所有已注册贡献者的返回值。
 * <p>
 * <b>设计动机</b>：宝石系统要为镶嵌宝石的装备提供词条加成，但宝石逻辑必须能从装备模块中
 * 完整剥离（第三方可编写无宝石的装备附属）。本 SPI 让宝石模块仅依赖 core 注册贡献者，
 * 装备模块对宝石模块零编译期依赖 —— 删除宝石模块，装备加成计算照常工作（无贡献者即无追加）。
 * <p>
 * <b>注册</b>：插件模块在自己的 {@code init()} 中调用
 * {@link EquipmentBonusCoordinator#register(IEquipmentBonusContributor)}，仿
 * {@code SnapshotCoordinator} 的贡献者注册模式。
 * <p>
 * <b>故障隔离</b>：单个贡献者抛异常不应影响其它贡献者或装备加成主流程，
 * {@link EquipmentBonusCoordinator#collectAll(ItemStack)} 已用 try-catch 包裹每个贡献者调用。
 *
 * @see EquipmentBonusCoordinator
 */
public interface IEquipmentBonusContributor {

    /**
     * 获取贡献者的唯一标识符
     * <p>
     * 用于日志和调试，应使用模块命名空间（如 "rpgcraftgemstone:socket_gem"）。
     *
     * @return 贡献者标识符字符串
     */
    String getContributorId();

    /**
     * 为一件装备贡献额外的属性加成
     * <p>
     * 由装备模块在计算该件装备的总加成时调用。返回的映射会被合并到该装备的加成总表中
     * （与装备自身的基础加成相加）。返回空映射表示该装备与本贡献者无关（如未镶嵌宝石）。
     * <p>
     * 实现应<b>只读</b>地检视 {@code stack}（读 DataComponent），不应修改入参。返回的映射
     * 应是新建的、可变的或不可变均无妨（调用方仅读取并合并）。
     *
     * @param stack 待计算的装备物品堆叠
     * @return 属性ID → 加成值的映射；空映射表示无贡献
     */
    Map<Identifier, EquipmentBonus> contribute(ItemStack stack);
}
