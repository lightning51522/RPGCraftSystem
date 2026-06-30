package com.rpgcraft.gemstone;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.jspecify.annotations.NonNull;

/**
 * gemstone 模块客户端事件处理（仅客户端）
 * <p>
 * 镜像加载 {@link SocketGemConfig}：tooltip 显示宝石词条数值（见
 * {@link SocketGemTooltipContributor}）时需查询数值表，但服务端的 reload 监听器
 * （{@link SocketGemConfig#onAddReloadListener}）不在客户端触发，故客户端需镜像加载同一份
 * JSON 到 {@link SocketGemConfig} 的静态状态。
 * <p>
 * 与 equipment 模块的 {@code RarityGemstoneConfig} 客户端镜像加载模式一致（装备模块由 client
 * 模块代理加载；本模块因独立于 client，自行注册客户端 reload 监听器加载自己的配置）。
 */
@EventBusSubscriber(modid = GemstoneMod.MODID, value = Dist.CLIENT)
public class GemstoneClientEventHandler {

    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onRegisterClientReload(AddClientReloadListenersEvent event) {
        event.addListener(SocketGemConfig.CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                return readConfig(rm);
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                SocketGemConfig.loadFromJson(json, true);
            }
        });
    }

    private static JsonObject readConfig(ResourceManager rm) {
        var resource = rm.getResource(SocketGemConfig.CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                GemstoneMod.LOGGER.warn("客户端加载镶嵌宝石词条配置失败: {} - {}",
                        SocketGemConfig.CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }
}
