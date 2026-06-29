package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;

/**
 * 稀有度宝石铁砧锻造处理器
 * <p>
 * 在铁砧中用 {@link RPGItems#RARITY_GEMSTONE 稀有度宝石} 作为材料（右槽）锻造一件已注册的可装备物品（左槽），
 * 有几率将其稀有度提升一级（每次最多 +1，最高 {@link EquipmentRarity#RAINBOW}）。
 * <p>
 * <b>预览/取出分离设计（方案 B，预览诚实）</b>：
 * <ul>
 *   <li>{@link #onAnvilUpdate 预览}：vanilla 对「剑+宝石」无配方、默认输出为空，故必须显式
 *       {@code setOutput(left.copy())} 才能产生预览。预览展示<b>原武器不变</b>（不修改稀有度），
 *       同时设置 {@code materialCost}（扣全额宝石）和少量 {@code xpCost}。两端（客户端+服务端）都执行
 *       同一份配置查询（{@link RarityGemstoneConfig} 服务端由 AddServerReloadListenersEvent 加载、
 *       客户端由 client 模块镜像加载），保证预览判断一致、无闪烁。</li>
 *   <li>{@link #onAnvilCraftPost 取出}：仅在服务端按目标稀有度的成功率掷骰：
 *     <ul>
 *       <li>成功 → 把刚取出的武器升级一级（写组件）</li>
 *       <li>失败 → 保留原武器，按 {@code failConsumeRate} 退回部分宝石</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * 随机必须放在取出阶段（而非预览），因为 {@link AnvilUpdateEvent} 每次输入变化（含改名键入）都会重触发，
 * 若预览随机会让玩家看到稀有度随按键跳动。
 * <p>
 * <b>取出后物品定位</b>（NeoForge 26.1 已验证）：
 * <ul>
 *   <li>普通点击：刚取出的武器在 {@code menu.getCarried()}（光标），改写后由
 *       {@code handleContainerClick} 末尾的 {@code broadcastChanges()} 自动同步给客户端。</li>
 *   <li>Shift 点击：武器已进背包、{@code getOutput()} 为空；在背包扫描同 itemId 且稀有度匹配的堆升级。
 *       若背包存在多件同类武器可能有歧义（升级其中一件）。</li>
 * </ul>
 * <p>
 * <b>适用范围</b>：左槽必须是已注册可装备物品（{@code IEquipmentRegistry.getBonuses} 非空），
 * 且当前稀有度 &lt; {@link EquipmentRarity#RAINBOW}。
 *
 * @see RarityGemstoneConfig 升级规则（消耗/概率/失败消耗比例）
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class RarityForgeHandler {

    /** 锻造的基础经验消耗（可在此扩展为配置项）。 */
    private static final int FORGE_XP_COST = 1;

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        // AnvilUpdateEvent 在客户端 + 服务端都触发。两端都通过同一份 RarityGemstoneConfig
        // （服务端由 AddServerReloadListenersEvent 加载，客户端由 client 模块镜像加载）查询规则，
        // 故预览判断两端一致，避免客户端闪烁。

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) return;

        // 右槽必须是稀有度宝石
        if (!isGemstone(right)) return;

        // 左槽必须是已注册可装备物品且可升级（当前稀有度 < RAINBOW）
        Identifier leftId = BuiltInRegistries.ITEM.getKey(left.getItem());
        if (RPGSystems.getEquipmentSystem().getRegistry().getBonuses(leftId).isEmpty()) return;
        EquipmentRarity current = currentRarity(left);
        if (current == EquipmentRarity.RAINBOW) return;

        // 查询升级规则（两端同一份配置）
        EquipmentRarity target = nextTier(current);
        RarityGemstoneConfig.UpgradeRule rule = RarityGemstoneConfig.getUpgradeRule(target);
        if (rule.gemCost() <= 0 || rule.chance() <= 0) return; // 该级不可锻造 → 不产生预览
        if (right.getCount() < rule.gemCost()) return;         // 宝石不足 → 不产生预览

        // 因 vanilla 对「剑+宝石」无配方、默认输出为空，这里必须显式 setOutput 才能产生预览。
        // 方案B：预览展示「原武器不变」（取出时再掷骰决定是否升级）。
        // 输出 = 左槽武器的副本（不修改稀有度）。两端都执行。
        event.setOutput(left.copy());
        event.setMaterialCost(rule.gemCost());
        event.setXpCost(FORGE_XP_COST);
    }

    @SubscribeEvent
    public static void onAnvilCraftPost(AnvilCraftEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack leftSnapshot = event.getLeft();
        ItemStack rightSnapshot = event.getRight();
        // 判定本次取出是否为宝石锻造：右槽是宝石 + 左槽是可装备物且稀有度 < RAINBOW
        if (rightSnapshot.isEmpty() || !isGemstone(rightSnapshot)) return;
        if (leftSnapshot.isEmpty()) return;
        Identifier leftId = BuiltInRegistries.ITEM.getKey(leftSnapshot.getItem());
        if (RPGSystems.getEquipmentSystem().getRegistry().getBonuses(leftId).isEmpty()) return;
        EquipmentRarity current = currentRarity(leftSnapshot);
        if (current == EquipmentRarity.RAINBOW) return;

        EquipmentRarity target = nextTier(current);
        RarityGemstoneConfig.UpgradeRule rule = RarityGemstoneConfig.getUpgradeRule(target);
        if (rule.gemCost() <= 0 || rule.chance() <= 0) return;

        // 掷骰（服务端权威随机源）
        boolean success = serverPlayer.getRandom().nextDouble() < rule.chance();

        if (success) {
            upgradeTakenWeapon(event, leftSnapshot, current, target);
            EquipmentMod.LOGGER.debug("稀有度宝石锻造成功：{} {} → {}（玩家 {}）",
                    leftId, current, target, serverPlayer.getName().getString());
        } else {
            // 失败：原版已按预览全额扣 gemCost 颗宝石，这里退回 (gemCost - 实际失败消耗) 颗
            int failConsume = (int) Math.ceil(rule.gemCost() * rule.failConsumeRate());
            int refund = Math.max(0, rule.gemCost() - failConsume);
            if (refund > 0) {
                ItemStack refundStack = new ItemStack(RPGItems.RARITY_GEMSTONE.get(), refund);
                serverPlayer.getInventory().placeItemBackInInventory(refundStack);
            }
            EquipmentMod.LOGGER.debug("稀有度宝石锻造失败：{} 保持 {}，退回 {} 颗宝石（玩家 {}）",
                    leftId, current, refund, serverPlayer.getName().getString());
        }
    }

    // ----------------------------------------------------------------
    // 取出后武器升级（普通点击 vs Shift 点击两路径）
    // ----------------------------------------------------------------

    /**
     * 把刚取出的武器升级一级。
     * <p>
     * 普通点击：武器在光标 {@code menu.getCarried()}，改写后自动同步客户端。
     * Shift 点击：武器已进背包、{@code getOutput()} 空，在背包扫描升级。
     *
     * @param event       取出事件（提供 menu 和 output 判据）
     * @param leftSnapshot 左槽取出前快照（用于 Shift 点击时按 itemId+原稀有度定位）
     * @param current     武器原稀有度
     * @param target      目标稀有度
     */
    private static void upgradeTakenWeapon(AnvilCraftEvent.Post event, ItemStack leftSnapshot,
                                           EquipmentRarity current, EquipmentRarity target) {
        ItemStack output = event.getOutput();
        AnvilMenu menu = event.getMenu();

        if (!output.isEmpty()) {
            // 普通点击：武器在光标
            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && isSameItem(carried, leftSnapshot)) {
                applyRarityUpgrade(carried, target);
                menu.setCarried(carried); // 由 handleContainerClick 末尾 broadcastChanges 自动同步
            }
        } else {
            // Shift 点击：武器已在背包，按 itemId + 原稀有度定位升级
            upgradeInInventory(event.getEntity().getInventory(), leftSnapshot, current, target);
        }
    }

    /**
     * 在玩家背包中定位刚放入的武器（itemId 匹配 + 稀有度 == 原值）并升级。
     * 若存在多件同类同稀有度武器，只升级第一件匹配的（已知歧义，详见类 javadoc）。
     */
    private static void upgradeInInventory(Inventory inv, ItemStack leftSnapshot,
                                           EquipmentRarity current, EquipmentRarity target) {
        Item item = leftSnapshot.getItem();
        // 主背包 + 热栏（共 36 格）
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            if (currentRarity(stack) != current) continue;
            applyRarityUpgrade(stack, target);
            return; // 仅升级第一件匹配
        }
        // 未定位到（理论上不应发生）：记录但不阻塞流程
        EquipmentMod.LOGGER.warn("稀有度宝石锻造成功但未在背包定位到目标武器（itemId 可能歧义），升级未应用");
    }

    /** 把稀有度组件设为目标值（写组件；GRAY 本无组件，升级到 WHITE 时首次写入）。 */
    private static void applyRarityUpgrade(ItemStack stack, EquipmentRarity target) {
        stack.set(RPGComponents.EQUIPMENT_RARITY.get(), target);
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    /** 读取堆叠的当前稀有度（无组件视为 GRAY）。 */
    private static EquipmentRarity currentRarity(ItemStack stack) {
        EquipmentRarity r = stack.get(RPGComponents.EQUIPMENT_RARITY.get());
        return r != null ? r : EquipmentRarity.GRAY;
    }

    /** 目标稀有度 = 序号 +1，cap 在 RAINBOW。 */
    private static EquipmentRarity nextTier(EquipmentRarity current) {
        EquipmentRarity[] tiers = EquipmentRarity.values();
        int idx = current.ordinal();
        return idx < tiers.length - 1 ? tiers[idx + 1] : EquipmentRarity.RAINBOW;
    }

    /** 判断堆叠是否为稀有度宝石。 */
    private static boolean isGemstone(ItemStack stack) {
        return stack.getItem() == RPGItems.RARITY_GEMSTONE.get();
    }

    /** 判断两个堆叠是否为同一物品（仅比 Item，不比组件/数量）。 */
    private static boolean isSameItem(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }
}
