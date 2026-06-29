package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * 装备等级铁砧升级处理器
 * <p>
 * 在铁砧中用<b>同物品 ID 的另一件装备</b>（右槽）作为材料升级左槽装备的等级（每次 +1，最高 6，<b>无失败</b>）。
 * <p>
 * 规则：
 * <ul>
 *   <li>左/右槽为<b>同物品 ID</b>（不要求同稀有度）</li>
 *   <li>两者均为已注册可装备物（{@code IEquipmentRegistry.getBonuses} 非空）</li>
 *   <li>两者<b>当前等级相同</b>且 &lt; 6</li>
 * </ul>
 * 命中 → 输出左槽装备副本（等级 +1），消耗右槽 1 件。
 * <p>
 * <b>与 {@link RarityForgeHandler}（宝石锻造）互斥</b>：本处理器显式排除右槽为宝石的情况，
 * 且条件「左/右同装备物品」与「右槽为宝石」本身不相交，故两个 AnvilUpdateEvent 订阅器不会冲突。
 * <p>
 * 因输出在预览阶段（AnvilUpdateEvent）就已确定、无随机，<b>无需</b> AnvilCraftEvent.Post。
 * 两端（客户端+服务端）都执行——等级判定直接读组件，无需配置镜像。
 *
 * @see RarityForgeHandler 稀有度宝石锻造（另一条铁砧升级路径）
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentLevelForgeHandler {

    /** 等级升级的基础经验消耗。 */
    private static final int UPGRADE_XP_COST = 1;
    /** 装备等级上限（与 EQUIPMENT_LEVEL 组件的 intRange 一致）。 */
    private static final int MAX_LEVEL = 6;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;

        // 防御性：右槽是稀有度宝石则交给 RarityForgeHandler，不在此处理
        if (right.getItem() == RPGItems.RARITY_GEMSTONE.get()) return;

        // 左/右必须为同物品 ID
        if (left.getItem() != right.getItem()) return;

        // 必须为已注册可装备物
        Identifier itemId = BuiltInRegistries.ITEM.getKey(left.getItem());
        if (RPGSystems.getEquipmentSystem().getRegistry().getBonuses(itemId).isEmpty()) return;

        // 两者当前等级相同且 < 上限
        int leftLevel = currentLevel(left);
        int rightLevel = currentLevel(right);
        if (leftLevel != rightLevel) return;
        if (leftLevel >= MAX_LEVEL) return;

        // 输出左槽装备副本，等级 +1（钳制到上限）
        ItemStack result = left.copy();
        result.set(RPGComponents.EQUIPMENT_LEVEL.get(), Math.min(MAX_LEVEL, leftLevel + 1));
        event.setOutput(result);
        event.setMaterialCost(1);
        event.setXpCost(UPGRADE_XP_COST);
    }

    /** 读取堆叠的当前装备等级（无组件视为 0）。 */
    private static int currentLevel(ItemStack stack) {
        Integer level = stack.get(RPGComponents.EQUIPMENT_LEVEL.get());
        return level != null ? level : 0;
    }
}
