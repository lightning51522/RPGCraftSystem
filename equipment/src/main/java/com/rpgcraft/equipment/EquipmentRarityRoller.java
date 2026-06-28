package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 装备稀有度随机生成器
 * <p>
 * 在装备<b>实例</b>进入世界时按 {@link EquipmentRarityConfig} 的概率随机赋予一个稀有度等级，
 * 写入 {@link RPGComponents#EQUIPMENT_RARITY} 组件。所有装备基础为 {@link EquipmentRarity#GRAY}。
 * <p>
 * <b>优先级</b>（{@link #resolveRarity}）：
 * <ol>
 *   <li>Stack 已有组件（已生成过的物品）—— 直接用之（幂等，防 chunk 重载/存档加载重复随机）</li>
 *   <li>注册表固定稀有度覆盖（{@code IEquipmentRegistry.getRarity(itemId)} 非 GRAY）—— 用之</li>
 *   <li>随机生成（按概率表）</li>
 * </ol>
 * <p>
 * <b>触发点</b>：{@link EntityJoinLevelEvent} —— 覆盖怪物掉落 / 宝箱 / 钓鱼 / 发射器等所有进入世界的
 * {@link ItemEntity}。
 * <p>
 * <b>限制</b>：NeoForge 26.1 已移除合成事件（{@code ItemCraftedEvent} 等均不存在），故玩家合成出的装备
 * <b>不参与</b>随机稀有度（恒为 GRAY）。仅「进入世界」的物品（掉落/宝箱/钓鱼）会随机。
 * <p>
 * 仅服务端处理（随机与组件写入需在权威端执行）。客户端物品的组件由 NeoForge 自动同步。
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentRarityRoller {

    private EquipmentRarityRoller() {
    }

    @SubscribeEvent
    public static void onItemJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;
        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;
        applyRarity(stack, itemEntity.getRandom());
    }

    /**
     * 为物品堆叠赋予稀有度组件（幂等：已有组件则不变）。
     */
    private static void applyRarity(ItemStack stack, RandomSource random) {
        // 幂等：已有组件则不再生成（防存档加载/chunk 重载重复随机）
        if (stack.has(RPGComponents.EQUIPMENT_RARITY.get())) return;

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        EquipmentRarity rarity = resolveRarity(stack, itemId, random);
        // GRAY 是默认（无组件时 getOrDefault 返回 GRAY），写不写无视觉差异；
        // 为节省 NBT 体积，仅写非 GRAY 等级。
        if (rarity != EquipmentRarity.GRAY) {
            stack.set(RPGComponents.EQUIPMENT_RARITY.get(), rarity);
        }
    }

    /**
     * 解析某物品堆叠应使用的稀有度（优先级：组件 > 注册表固定覆盖 > 随机）。
     */
    public static EquipmentRarity resolveRarity(ItemStack stack, Identifier itemId, RandomSource random) {
        // 1. 已有组件
        EquipmentRarity existing = stack.get(RPGComponents.EQUIPMENT_RARITY.get());
        if (existing != null) return existing;

        // 2. 注册表固定稀有度覆盖（equipment_attributes.json 中显式设了 rarity 的物品）
        EquipmentRarity fixed = RPGSystems.getEquipmentSystem().getRegistry().getRarity(itemId);
        if (fixed != EquipmentRarity.GRAY) return fixed;

        // 3. 随机生成
        return EquipmentRarityConfig.rollRarity(random);
    }
}
