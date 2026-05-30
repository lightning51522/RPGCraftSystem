package com.rpgcraft.core.attribute;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.RPGCraftCore;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.Executor;

/**
 * 生物属性 JSON 配置加载器
 * <p>
 * 从 {@code data/rpgcraftcore/rpg/mob_attributes.json} 读取各生物类型的自定义属性配置，
 * 在服务端资源加载/重载时解析并存入内存 Map。
 * <p>
 * 通过 {@link AddServerReloadListenersEvent} 注册为自定义资源重载监听器，
 * 支持 {@code /reload} 命令热更新配置。
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class MobAttributeConfig {

    /**
     * 单个生物类型的属性配置
     *
     * @param life          生命值
     * @param strength      力量（物理攻击力基准）
     * @param defense       防御力
     * @param resistance    法术抗性（百分比）
     * @param criticalRate  暴击率（百分比）
     * @param criticalRatio 暴击伤害加成（百分比）
     */
    public record MobAttributes(
            int life, int strength, int defense,
            int resistance, int criticalRate, int criticalRatio
    ) {}

    /** 配置文件路径 */
    private static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/mob_attributes.json");

    /** 生物类型ID → 属性配置 的映射，不可变快照 */
    private static volatile Map<Identifier, MobAttributes> configMap = Collections.emptyMap();

    /**
     * 注册资源重载监听器（Game 事件总线）
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                try {
                    var resource = resourceManager.getResource(CONFIG_ID);
                    if (resource.isPresent()) {
                        try (var reader = resource.get().openAsReader()) {
                            return new Gson().fromJson(reader, JsonObject.class);
                        }
                    }
                } catch (Exception e) {
                    RPGCraftCore.LOGGER.error("加载生物属性配置失败", e);
                }
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                Map<Identifier, MobAttributes> newMap = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    try {
                        Identifier entityId = Identifier.parse(entry.getKey());
                        JsonObject attrs = entry.getValue().getAsJsonObject();
                        newMap.put(entityId, new MobAttributes(
                                attrs.getAsJsonPrimitive("life").getAsInt(),
                                attrs.getAsJsonPrimitive("strength").getAsInt(),
                                attrs.getAsJsonPrimitive("defense").getAsInt(),
                                attrs.getAsJsonPrimitive("resistance").getAsInt(),
                                attrs.getAsJsonPrimitive("critical_rate").getAsInt(),
                                attrs.getAsJsonPrimitive("critical_ratio").getAsInt()
                        ));
                    } catch (Exception e) {
                        RPGCraftCore.LOGGER.warn("解析生物属性配置失败: {} - {}", entry.getKey(), e.getMessage());
                    }
                }
                configMap = Collections.unmodifiableMap(newMap);
                RPGCraftCore.LOGGER.info("已加载 {} 种生物的自定义属性配置", newMap.size());
            }
        });
    }

    /**
     * 查询指定生物类型的属性配置
     *
     * @param entityType 生物类型的 Identifier（如 minecraft:zombie）
     * @return 属性配置，未配置则返回 empty
     */
    public static Optional<MobAttributes> getConfig(Identifier entityType) {
        return Optional.ofNullable(configMap.get(entityType));
    }
}
