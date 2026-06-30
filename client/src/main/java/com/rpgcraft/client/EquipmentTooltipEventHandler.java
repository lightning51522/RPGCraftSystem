package com.rpgcraft.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 装备属性加成 Tooltip 显示 + 客户端配置加载
 * <p>
 * 负责两件事：
 * <ol>
 *   <li>在客户端注册资源重载监听器，加载装备配置用于 tooltip 显示</li>
 *   <li>监听 {@link ItemTooltipEvent}，在物品 tooltip 中追加 RPG 属性加成信息和稀有度</li>
 * </ol>
 * <p>
 * 通过 {@link RPGSystems#getEquipmentSystem()} 访问装备注册中心，
 * 避免直接依赖 equipment 模块的实现类。
 */
@EventBusSubscriber(modid = ClientMod.MODID, value = Dist.CLIENT)
public class EquipmentTooltipEventHandler {

    private static final Gson GSON = new Gson();

    @SubscribeEvent
    static void onRegisterClientReload(AddClientReloadListenersEvent event) {
        Identifier configId = RPGSystems.getEquipmentSystem().getConfigId();
        event.addListener(configId, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                return readJson(resourceManager, configId, "装备属性配置");
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                RPGSystems.getEquipmentSystem().getRegistry().loadFromJson(json);
            }
        });

        // 稀有度宝石锻造配置客户端镜像：铁砧锻造预览（AnvilUpdateEvent）在客户端也触发，需读取 gemCost
        // 决定是否展示预览；但服务端 reload 监听器不在客户端触发，故此处镜像加载同一 JSON。
        Identifier gemstoneConfigId = RPGSystems.getEquipmentSystem().getGemstoneConfigId();
        if (gemstoneConfigId != null) {
            event.addListener(gemstoneConfigId, new SimplePreparableReloadListener<JsonObject>() {
                @Override
                protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                    return readJson(resourceManager, gemstoneConfigId, "稀有度宝石锻造配置");
                }

                @Override
                protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                    RPGSystems.getEquipmentSystem().applyGemstoneConfig(json);
                }
            });
        }

        // 装备加成系数配置客户端镜像：tooltip 显示加成数值时需按稀有度/等级系数缩放，
        // 与服务端 calculateTotalBonus 同口径；服务端 reload 监听器不在客户端触发，故镜像加载。
        Identifier multiplierConfigId = RPGSystems.getEquipmentSystem().getBonusMultiplierConfigId();
        if (multiplierConfigId != null) {
            event.addListener(multiplierConfigId, new SimplePreparableReloadListener<JsonObject>() {
                @Override
                protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                    return readJson(resourceManager, multiplierConfigId, "装备加成系数配置");
                }

                @Override
                protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                    RPGSystems.getEquipmentSystem().applyBonusMultiplierConfig(json);
                }
            });
        }
    }

    /** 读取一份 JSON 配置（找不到或解析失败返回空对象）。 */
    private static JsonObject readJson(ResourceManager resourceManager, Identifier configId, String displayName) {
        try {
            var resource = resourceManager.getResource(configId);
            if (resource.isPresent()) {
                try (var reader = resource.get().openAsReader()) {
                    return GSON.fromJson(reader, JsonObject.class);
                }
            }
        } catch (Exception e) {
            RPGCraftCore.LOGGER.error("客户端加载{}失败: {}", displayName, configId, e);
        }
        return new JsonObject();
    }

    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        // 宝石物品名称染色：携带 GEM_INSTANCE 组件的宝石按其稀有度染色（含 RAINBOW 逐 tick 渐变）。
        // 由 client 模块统一负责名称染色（能访问 EquipmentRarityColors 的彩虹动画）；gemstone 模块
        // 仅负责词条数值行。染色后继续后续装备逻辑（宝石不是装备，会在 bonuses 检查处自然 return）。
        com.rpgcraft.core.equipment.GemInstance gemInstance =
                stack.get(com.rpgcraft.core.equipment.RPGComponents.GEM_INSTANCE.get());
        if (gemInstance != null) {
            colorizeName(event.getToolTip(), gemInstance.rarity());
        }

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<Map<Identifier, EquipmentBonus>> bonuses = RPGSystems.getEquipmentSystem().getRegistry().getBonuses(itemId);
        if (bonuses.isEmpty()) return;

        // 稀有度优先级：stack 组件（动态随机生成）> 注册表固定覆盖 > GRAY
        EquipmentRarity rarity = stack.get(com.rpgcraft.core.equipment.RPGComponents.EQUIPMENT_RARITY.get());
        if (rarity == null) {
            rarity = RPGSystems.getEquipmentSystem().getRegistry().getRarity(itemId);
        }
        // 按该件装备稀有度与等级缩放加成显示（与服务端 calculateTotalBonus 口径一致：稀有度系数×等级系数，向下取整）
        // 装备等级（>0 时在名称后追加星形后缀）
        int level = stack.getOrDefault(com.rpgcraft.core.equipment.RPGComponents.EQUIPMENT_LEVEL.get(), 0);
        double multiplier = RPGSystems.getEquipmentSystem().getRarityMultiplier(rarity.getTier())
                * RPGSystems.getEquipmentSystem().getLevelMultiplier(level);

        // 非最低（GRAY）稀有度 或 有装备等级 → 改写名称行：染稀有度颜色（若有）并追加等级星
        if (rarity != EquipmentRarity.GRAY || level > 0) {
            List<Component> tooltip = event.getToolTip();
            if (!tooltip.isEmpty()) {
                Component originalName = tooltip.getFirst();
                StringBuilder name = new StringBuilder(originalName.getString());
                if (level > 0) {
                    name.append(' ').append(com.rpgcraft.core.equipment.EquipmentLevelStars.stars(level));
                }
                MutableComponent styled = Component.literal(name.toString());
                if (rarity != EquipmentRarity.GRAY) {
                    int color = EquipmentRarityColors.resolveColor(rarity);
                    styled.withStyle(s -> s.withColor(color));
                }
                tooltip.set(0, styled);
            }
        }

        // 追加属性加成（按稀有度缩放显示）
        for (Map.Entry<Identifier, EquipmentBonus> entry : bonuses.get().entrySet()) {
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(entry.getKey());
            if (attrEntry == null) continue;

            int value = (int) Math.floor(entry.getValue().value() * multiplier);
            String text = "§a" + attrEntry.getDisplayName() + " +" + value;
            event.getToolTip().add(Component.literal(text));
        }
    }

    /**
     * 按稀有度给 tooltip 的物品名第一行染色（含 RAINBOW 逐 tick 渐变）。
     * <p>
     * 仅染色，不追加等级星（宝石无等级）。GRAY 不染色（保持原版默认色）。
     * 供宝石物品名称染色调用（装备名称染色因含等级星后缀，在 {@link #onItemTooltip} 内联处理）。
     *
     * @param tooltip 物品 tooltip 行列表
     * @param rarity  宝石稀有度
     */
    private static void colorizeName(List<Component> tooltip, EquipmentRarity rarity) {
        if (rarity == EquipmentRarity.GRAY || tooltip.isEmpty()) return;
        Component originalName = tooltip.getFirst();
        MutableComponent styled = Component.literal(originalName.getString());
        styled.withStyle(s -> s.withColor(EquipmentRarityColors.resolveColor(rarity)));
        tooltip.set(0, styled);
    }
}
