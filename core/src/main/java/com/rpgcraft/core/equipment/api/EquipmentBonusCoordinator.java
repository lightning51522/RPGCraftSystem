package com.rpgcraft.core.equipment.api;

import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.equipment.EquipmentBonus;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装备加成贡献者协调器
 * <p>
 * 管理 {@link IEquipmentBonusContributor} 注册表，并为装备加成计算提供一个聚合入口
 * {@link #collectAll(ItemStack)}。装备模块的 {@code DefaultEquipmentHandler} 在为每件装备
 * 算完自身基础加成后，调用本方法合并所有外部贡献者的加成。
 * <p>
 * <b>设计</b>：仿 {@code SnapshotCoordinator} 的贡献者协调模式 —— 静态注册表（按 ID 去重）、
 * 故障隔离（单个贡献者抛异常不影响其它）。无任何贡献者时，{@link #collectAll} 返回空映射，
 * 装备加成计算回退到原始行为（仅基础加成）。
 * <p>
 * <b>线程安全</b>：注册表使用 {@link ConcurrentHashMap}。{@code collectAll} 每次新建结果映射，
 * 不共享可变状态。
 *
 * @see IEquipmentBonusContributor
 */
public final class EquipmentBonusCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentBonusCoordinator.class);

    /** 已注册的加成贡献者，按贡献者 ID 索引（与 SnapshotCoordinator 同模式）。 */
    private static final Map<String, IEquipmentBonusContributor> contributors = new ConcurrentHashMap<>();

    private EquipmentBonusCoordinator() {
        // 禁止实例化
    }

    /**
     * 注册加成贡献者
     * <p>
     * 插件模块在自己的 {@code init()} 中调用。重复注册同一 ID 会被拒绝并输出 WARN
     * （与 {@code RPGSystems} 的同优先级拒绝覆盖语义一致）。
     *
     * @param contributor 贡献者实例
     */
    public static void register(IEquipmentBonusContributor contributor) {
        String id = contributor.getContributorId();
        if (contributors.putIfAbsent(id, contributor) != null) {
            LOGGER.warn("装备加成贡献者 {} 已注册，拒绝重复注册", id);
            return;
        }
        LOGGER.debug("装备加成贡献者已注册: {}", id);
    }

    /**
     * 获取所有已注册的贡献者（不可变视图）。
     *
     * @return 贡献者集合
     */
    public static Collection<IEquipmentBonusContributor> getContributors() {
        return Collections.unmodifiableCollection(contributors.values());
    }

    /**
     * 聚合一件装备上所有外部贡献者提供的属性加成
     * <p>
     * 遍历所有已注册的 {@link IEquipmentBonusContributor}，收集每个贡献者对该装备的贡献，
     * 按 属性ID 累加合并。单个贡献者抛异常会被捕获并记录，不影响其它贡献者或返回结果。
     * <p>
     * 供装备模块的 {@code DefaultEquipmentHandler.calculateTotalBonus} 在算完基础加成后调用，
     * 把返回值合并进总加成表。无任何贡献者注册时返回空映射（装备加成计算回退到原始行为）。
     *
     * @param stack 待计算的装备物品堆叠
     * @return 属性ID → 累计加成值的映射（总是非 null，可能为空）
     */
    public static Map<Identifier, EquipmentBonus> collectAll(ItemStack stack) {
        if (contributors.isEmpty()) {
            return Map.of();
        }
        Map<Identifier, EquipmentBonus> collected = new HashMap<>();
        for (IEquipmentBonusContributor contributor : contributors.values()) {
            try {
                Map<Identifier, EquipmentBonus> contribution = contributor.contribute(stack);
                if (contribution == null || contribution.isEmpty()) continue;
                contribution.forEach((attrId, bonus) ->
                        collected.merge(attrId, bonus, EquipmentBonus::add));
            } catch (Exception e) {
                LOGGER.error("装备加成贡献者 [{}] 计算失败: {}",
                        contributor.getContributorId(), e.getMessage(), e);
            }
        }
        return collected;
    }
}
