package com.rpgcraft.profession;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

/**
 * 职业模块配置服务端重载监听器
 * <p>
 * 从 {@code data/rpgcraftcore/rpg/profession_config.json} 读取行为开关：
 * <ul>
 *   <li>{@code allow_downgrade_switch}（bool，默认 {@code false}）：是否允许从进阶职业切回基础职业。
 *       默认禁止。无论此开关如何，同分支进阶叶子职业之间（如同时进阶的两条分支）可互相切换。</li>
 * </ul>
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionConfig {

    private static final Gson GSON = new Gson();

    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/profession_config.json");

    private static final boolean DEFAULT_ALLOW_DOWNGRADE_SWITCH = false;

    private static volatile boolean allowDowngradeSwitch = DEFAULT_ALLOW_DOWNGRADE_SWITCH;

    private ProfessionConfig() {
    }

    public static boolean isAllowDowngradeSwitch() {
        return allowDowngradeSwitch;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager,
                                                  @NonNull ProfilerFiller profiler) {
                try {
                    var resource = resourceManager.getResource(CONFIG_ID);
                    if (resource.isPresent()) {
                        try (var reader = resource.get().openAsReader()) {
                            return GSON.fromJson(reader, JsonObject.class);
                        }
                    }
                } catch (Exception e) {
                    ProfessionMod.LOGGER.error("加载职业模块配置失败", e);
                }
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager,
                                 @NonNull ProfilerFiller profiler) {
                allowDowngradeSwitch = parseBoolean(json, "allow_downgrade_switch", DEFAULT_ALLOW_DOWNGRADE_SWITCH);
                ProfessionMod.LOGGER.info("职业模块配置已加载：allow_downgrade_switch={}", allowDowngradeSwitch);
            }
        });
    }

    private static boolean parseBoolean(JsonObject json, String key, boolean defaultValue) {
        if (json.has(key)) {
            try {
                return json.getAsJsonPrimitive(key).getAsBoolean();
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("职业配置字段 {} 解析失败，使用默认值 {}（异常：{}）",
                        key, defaultValue, e.getMessage());
            }
        }
        return defaultValue;
    }
}
