package com.rpgcraft.attributepoints;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rpgcraft.core.network.SyncAttributePointsConfigPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.NonNull;

/**
 * 属性点模块配置服务端重载监听器
 * <p>
 * 从 {@code data/rpgcraftcore/rpg/attribute_points_config.json} 读取模块行为开关，
 * 当前支持：
 * <ul>
 *   <li>{@code allow_decrease}（bool，默认 {@code true}）：是否允许减少/回收已分配的属性点。
 *       禁用时服务端 {@link AttributePointsManager#deallocate} 拒绝所有回收请求，
 *       客户端角色界面隐藏 {@code [-]} 按钮。</li>
 * </ul>
 * <p>
 * 配置在玩家登录时推送（{@link AttributePointsLoginEventHandler}），
 * 在 {@code /reload} 后对在线玩家即时推送。
 * <p>
 * 遵循项目现有配置加载模式（参考 {@code EquipmentConfig} / {@code MobAttributeConfig}）：
 * {@link SimplePreparableReloadListener} + {@link AddServerReloadListenersEvent}，
 * 命名空间恒为 {@code rpgcraftcore}，字段命名 {@code snake_case}。
 */
@EventBusSubscriber(modid = AttributePointsMod.MODID)
public class AttributePointsConfig {

    private static final Gson GSON = new Gson();

    /** 配置文件定位 ID（命名空间遵循项目约定：恒为 rpgcraftcore） */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/attribute_points_config.json");

    /** 代码默认值：允许减少（文件缺失或字段缺失时使用此值） */
    private static final boolean DEFAULT_ALLOW_DECREASE = true;

    /** 当前是否允许减少属性点（volatile 保证重载线程与查询线程的可见性） */
    private static volatile boolean allowDecrease = DEFAULT_ALLOW_DECREASE;

    private AttributePointsConfig() {
        // 禁止实例化
    }

    /**
     * 查询是否允许减少属性点
     *
     * @return {@code true} 允许；{@code false} 禁止
     */
    public static boolean isAllowDecrease() {
        return allowDecrease;
    }

    /**
     * 将当前配置推送给指定玩家（登录时调用）
     *
     * @param player 目标玩家
     */
    public static void syncToClient(ServerPlayer player) {
        SyncAttributePointsConfigPacket.sendToClient(player, allowDecrease);
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
                    AttributePointsMod.LOGGER.error("加载属性点模块配置失败", e);
                }
                // 文件缺失/解析失败 → 空对象，apply 中按字段缺失处理（回退默认值）
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager,
                                 @NonNull ProfilerFiller profiler) {
                allowDecrease = parseBoolean(json, "allow_decrease", DEFAULT_ALLOW_DECREASE);
                AttributePointsMod.LOGGER.info("属性点模块配置已加载：allow_decrease={}", allowDecrease);

                // /reload 后对在线玩家即时推送新值
                pushToOnlinePlayers();
            }
        });
    }

    /**
     * 解析 JSON 布尔字段，缺失或类型错误时回退默认值
     */
    private static boolean parseBoolean(JsonObject json, String key, boolean defaultValue) {
        if (json.has(key)) {
            try {
                return json.getAsJsonPrimitive(key).getAsBoolean();
            } catch (Exception e) {
                AttributePointsMod.LOGGER.warn("属性点配置字段 {} 解析失败，使用默认值 {}（异常：{}）",
                        key, defaultValue, e.getMessage());
            }
        }
        return defaultValue;
    }

    /**
     * 向所有在线玩家推送当前配置（/reload 后生效）
     */
    private static void pushToOnlinePlayers() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToClient(player);
        }
    }
}
