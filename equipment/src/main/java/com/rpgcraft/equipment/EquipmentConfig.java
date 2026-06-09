package com.rpgcraft.equipment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

/**
 * 装备配置服务端重载监听器
 * <p>
 * 仅负责注册 NeoForge 重载监听器，将解析委托到
 * {@link DefaultEquipmentRegistry#loadFromJson(JsonObject)}。
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentConfig {

    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
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
                    EquipmentMod.LOGGER.error("加载装备属性配置失败", e);
                }
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                EquipmentManager.getRegistry().loadFromJson(json);
            }
        });
    }
}
