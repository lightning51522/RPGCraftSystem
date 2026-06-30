package com.rpgcraft.gemstone;

import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.GemInstance;
import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * 镶嵌宝石铁砧处理器
 * <p>
 * 在铁砧中用 {@link GemstoneItems#WATERMELON_TOURMALINE 镶嵌宝石} 作为材料（右槽）锻造一件已注册的可装备物品
 * （左槽），将宝石镶嵌到装备上（每件装备 1 颗，<b>无失败</b>）。
 * <p>
 * <b>规则</b>：
 * <ul>
 *   <li>左槽必须是已注册可装备物（{@code IEquipmentRegistry.getBonuses} 非空）</li>
 *   <li>左槽<b>尚未镶嵌</b>（无 EQUIPMENT_SOCKET 组件）—— 每件装备仅 1 颗，不可重复镶嵌</li>
 *   <li>右槽必须是镶嵌宝石，且携带 {@code GEM_INSTANCE} 组件</li>
 *   <li>宝石稀有度不得超过装备稀有度两级以上（{@code gemTier <= equipTier + 2}）</li>
 * </ul>
 * 命中 → 输出左槽装备副本（写入 EQUIPMENT_SOCKET 组件 = 右槽宝石实例），消耗右槽 1 颗。
 * <p>
 * <b>与其他铁砧处理器的互斥</b>：本处理器只认 {@link GemstoneItems#WATERMELON_TOURMALINE}（gemstone 模块物品），
 * equipment 模块的两个处理器各认 {@code RARITY_GEMSTONE}（稀有度宝石）/ 同 ID 装备 —— 三者右槽物品
 * 不相交，天然互斥，无需修改 equipment 的任何处理器。
 * <p>
 * 因输出在预览阶段（{@link AnvilUpdateEvent}）就已完全确定、无随机，<b>无需</b>
 * {@code AnvilCraftEvent.Post}。两端（客户端+服务端）都执行。
 *
 * @see com.rpgcraft.equipment.EquipmentLevelForgeHandler 同型参考（确定性铁砧升级）
 */
@EventBusSubscriber(modid = GemstoneMod.MODID)
public class SocketGemForgeHandler {

    /** 镶嵌的基础经验消耗。 */
    private static final int SOCKET_XP_COST = 1;
    /** 宝石稀有度相对装备稀有度的最大允许超出量（gemTier <= equipTier + 2）。 */
    private static final int MAX_RARITY_GAP = 2;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;

        // 右槽必须是本模块的镶嵌宝石
        if (right.getItem() != GemstoneItems.WATERMELON_TOURMALINE.get()) return;

        // 右槽宝石必须携带 GEM_INSTANCE 组件（描述这颗宝石的稀有度与词条）
        GemInstance gem = right.get(RPGComponents.GEM_INSTANCE.get());
        if (gem == null) return;

        // 左槽必须是已注册可装备物
        Identifier leftId = BuiltInRegistries.ITEM.getKey(left.getItem());
        if (RPGSystems.getEquipmentSystem().getRegistry().getBonuses(leftId).isEmpty()) return;

        // 左槽尚未镶嵌（每件装备 1 颗）
        if (left.get(RPGComponents.EQUIPMENT_SOCKET.get()) != null) return;

        // 宝石稀有度不得超过装备稀有度两级以上
        EquipmentRarity equipRarity = left.getOrDefault(
                RPGComponents.EQUIPMENT_RARITY.get(), EquipmentRarity.GRAY);
        if (gem.rarity().getTier() > equipRarity.getTier() + MAX_RARITY_GAP) return;

        // 输出左槽装备副本，写入镶嵌宝石实例（确定性，无随机）
        ItemStack result = left.copy();
        result.set(RPGComponents.EQUIPMENT_SOCKET.get(), gem);
        event.setOutput(result);
        event.setMaterialCost(1);
        event.setXpCost(SOCKET_XP_COST);
    }
}
