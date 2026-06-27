package com.rpgcraft.profession;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * 职业全局配置加载器
 * <p>
 * 仅负责加载 {@code data/rpgcraftcore/rpg/profession_config.json}（{@code allow_downgrade_switch}
 * 与 {@code default_max_level}）。具体职业定义已迁移到 {@code professions} 子模块的 Java 类，
 * 不再通过 datapack JSON 描述。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新配置。
 *
 * @see ProfessionManager
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionConfigLoader {

    /** 职业命名空间（与其它 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 全局配置文件 ID */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/profession_config.json");

    private static final Gson GSON = new Gson();

    /** 全局配置：是否允许从进阶职业切回基础职业 */
    private static volatile boolean allowDowngradeSwitch = false;
    /** 全局配置：职业默认等级上限（职业未指定 max_level 时使用） */
    private static volatile int defaultMaxLevel = 20;
    /** 全局配置：解锁副职业消耗的职业经验（基础副职业直接消耗；非基础副职业需前置满级后再消耗） */
    private static volatile int secondaryUnlockCost = 50000;

    public static boolean isAllowDowngradeSwitch() {
        return allowDowngradeSwitch;
    }

    public static int getDefaultMaxLevel() {
        return defaultMaxLevel;
    }

    public static int getSecondaryUnlockCost() {
        return secondaryUnlockCost;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // 注入服务器实例到 ProfessionManager，供 /reload 后遍历在线玩家同步职业状态
        ProfessionManager.setCurrentServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ProfessionManager.setCurrentServer(null);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/profession_config"),
                new SimplePreparableReloadListener<JsonObject>() {
                    @Override
                    protected @NonNull JsonObject prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return readConfig(rm);
                    }

                    @Override
                    protected void apply(@NonNull JsonObject json, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        applyConfig(json);
                    }
                });
    }

    private static JsonObject readConfig(ResourceManager rm) {
        var resource = rm.getResource(CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                ProfessionMod.LOGGER.warn("读取职业全局配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    private static void applyConfig(JsonObject json) {
        if (json == null) json = new JsonObject();
        allowDowngradeSwitch = parseBoolean(json, "allow_downgrade_switch", false);
        defaultMaxLevel = parseInt(json, "default_max_level", 20);
        if (defaultMaxLevel < 1) {
            ProfessionMod.LOGGER.warn("default_max_level 不能小于 1，已重置为 20");
            defaultMaxLevel = 20;
        }
        secondaryUnlockCost = parseInt(json, "secondary_unlock_cost", 50000);
        if (secondaryUnlockCost < 0) {
            ProfessionMod.LOGGER.warn("secondary_unlock_cost 不能为负，已重置为 50000");
            secondaryUnlockCost = 50000;
        }
        ProfessionMod.LOGGER.info("职业全局配置已加载：allow_downgrade_switch={}, default_max_level={}, secondary_unlock_cost={}",
                allowDowngradeSwitch, defaultMaxLevel, secondaryUnlockCost);
        // /reload 后向所有在线玩家推送新配置，保持客户端显示与服务端一致
        pushToOnlinePlayers();
    }

    // ==================================================================
    // 客户端配置同步
    // ==================================================================

    /**
     * 将当前配置推送给指定玩家（登录时调用）。
     */
    public static void syncToClient(net.minecraft.server.level.ServerPlayer player) {
        com.rpgcraft.core.network.SyncProfessionConfigPacket.sendToClient(
                player, secondaryUnlockCost, defaultMaxLevel, allowDowngradeSwitch);
    }

    /**
     * 向所有在线玩家推送当前配置（{@code /reload} 后调用）。
     */
    public static void pushToOnlinePlayers() {
        net.minecraft.server.MinecraftServer server = ProfessionManager.getCurrentServer();
        if (server == null) return;
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncToClient(player);
        }
    }

    private static boolean parseBoolean(JsonObject json, String key, boolean def) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            try {
                return json.getAsJsonPrimitive(key).getAsBoolean();
            } catch (Exception e) {
                ProfessionMod.LOGGER.debug("配置字段 {} 无法解析为布尔，使用默认值 {}", key, def, e);
            }
        }
        return def;
    }

    private static int parseInt(JsonObject json, String key, int def) {
        if (json.has(key) && json.get(key).isJsonPrimitive()) {
            try {
                return json.getAsJsonPrimitive(key).getAsInt();
            } catch (Exception e) {
                ProfessionMod.LOGGER.debug("配置字段 {} 无法解析为整数，使用默认值 {}", key, def, e);
            }
        }
        return def;
    }
}
