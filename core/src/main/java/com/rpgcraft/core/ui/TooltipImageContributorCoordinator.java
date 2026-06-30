package com.rpgcraft.core.ui;

import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tooltip 图像贡献者协调器
 * <p>
 * 管理 {@link ITooltipImageContributor} 注册表，并为 client 模块的 tooltip 渲染管线提供聚合入口
 * {@link #collectAll(ItemStack)}。client 的 {@code ClientTooltipComponent} 在为一件物品渲染
 * tooltip 前，调用本方法收集所有外部贡献者提供的图像数据。
 * <p>
 * <b>设计</b>：仿 {@code EquipmentBonusCoordinator} / {@code SnapshotCoordinator} 的贡献者协调
 * 模式 —— 静态注册表（按 ID 去重）、故障隔离（单个贡献者抛异常不影响其它）。无任何贡献者时，
 * {@link #collectAll} 返回空列表，tooltip 不追加任何图像元素。
 * <p>
 * <b>线程安全</b>：注册表使用 {@link ConcurrentHashMap}。
 *
 * @see ITooltipImageContributor
 * @see TooltipImageData
 */
public final class TooltipImageContributorCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TooltipImageContributorCoordinator.class);

    /** 已注册的 tooltip 图像贡献者，按贡献者 ID 索引。 */
    private static final ConcurrentHashMap<String, ITooltipImageContributor> contributors = new ConcurrentHashMap<>();

    private TooltipImageContributorCoordinator() {
        // 禁止实例化
    }

    /**
     * 注册 tooltip 图像贡献者
     * <p>
     * 插件模块在自己的 {@code init()} 中调用。重复注册同一 ID 会被拒绝并输出 WARN。
     *
     * @param contributor 贡献者实例
     */
    public static void register(ITooltipImageContributor contributor) {
        String id = contributor.getContributorId();
        if (contributors.putIfAbsent(id, contributor) != null) {
            LOGGER.warn("Tooltip 图像贡献者 {} 已注册，拒绝重复注册", id);
            return;
        }
        LOGGER.debug("Tooltip 图像贡献者已注册: {}", id);
    }

    /**
     * 获取所有已注册的贡献者（不可变视图）。
     *
     * @return 贡献者集合
     */
    public static Collection<ITooltipImageContributor> getContributors() {
        return Collections.unmodifiableCollection(contributors.values());
    }

    /**
     * 聚合一件物品上所有外部贡献者提供的 tooltip 图像数据
     * <p>
     * 遍历所有已注册的 {@link ITooltipImageContributor}，收集每个贡献者对该物品的非 null 贡献。
     * 单个贡献者抛异常会被捕获并记录，不影响其它贡献者或返回结果。
     * <p>
     * 供 client 模块的 tooltip 渲染管线调用。无任何贡献者注册时返回空列表（tooltip 无图像元素）。
     *
     * @param stack 待显示 tooltip 的物品堆叠
     * @return 图像数据列表（总是非 null，可能为空）
     */
    public static List<TooltipImageData> collectAll(ItemStack stack) {
        if (contributors.isEmpty()) {
            return List.of();
        }
        List<TooltipImageData> collected = new ArrayList<>();
        for (ITooltipImageContributor contributor : contributors.values()) {
            try {
                TooltipImageData data = contributor.contribute(stack);
                if (data != null) {
                    collected.add(data);
                }
            } catch (Exception e) {
                LOGGER.error("Tooltip 图像贡献者 [{}] 收集失败: {}",
                        contributor.getContributorId(), e.getMessage(), e);
            }
        }
        return collected;
    }
}
