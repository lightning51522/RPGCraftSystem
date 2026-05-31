package com.rpgcraft.core.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.equipment.DefaultEquipmentRegistry;
import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.equipment.EquipmentManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

import java.util.Map;
import java.util.Optional;

/**
 * 装备属性加成 Tooltip 显示 + 客户端配置加载
 * <p>
 * 负责两件事：
 * <ol>
 *   <li>在客户端注册资源重载监听器，加载装备配置用于 tooltip 显示</li>
 *   <li>监听 {@link ItemTooltipEvent}，在物品 tooltip 中追加 RPG 属性加成信息</li>
 * </ol>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID, value = Dist.CLIENT)
public class EquipmentTooltipHandler {

    private static final Gson GSON = new Gson();

    /**
     * 客户端资源重载监听器注册（自动路由到 Mod 总线）
     */
    @SubscribeEvent
    static void onRegisterClientReload(AddClientReloadListenersEvent event) {
        event.addListener(DefaultEquipmentRegistry.CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                try {
                    var resource = resourceManager.getResource(DefaultEquipmentRegistry.CONFIG_ID);
                    if (resource.isPresent()) {
                        try (var reader = resource.get().openAsReader()) {
                            return GSON.fromJson(reader, JsonObject.class);
                        }
                    }
                } catch (Exception e) {
                    RPGCraftCore.LOGGER.error("客户端加载装备属性配置失败", e);
                }
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                ((DefaultEquipmentRegistry) EquipmentManager.getRegistry()).loadFromJson(json);
            }
        });
    }

    /**
     * 在物品 tooltip 中追加 RPG 属性加成
     */
    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Optional<Map<Identifier, EquipmentBonus>> bonuses = EquipmentManager.getRegistry().getBonuses(itemId);
        if (bonuses.isEmpty()) return;

        for (Map.Entry<Identifier, EquipmentBonus> entry : bonuses.get().entrySet()) {
            IAttributeEntry attrEntry = GenericEntityData.getRegistry().getEntry(entry.getKey());
            if (attrEntry == null) continue;

            int value = entry.getValue().value();
            String text = "§a" + attrEntry.getDisplayName() + " +" + value;
            event.getToolTip().add(Component.literal(text));
        }
    }
}
