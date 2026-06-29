package com.rpgcraft.equipment;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

/**
 * 装备属性加成系数配置加载器
 * <p>
 * 加载 {@code data/rpgcraftcore/rpg/equipment_bonus_multipliers.json}：两个「每级增幅」常量。
 * <ul>
 *   <li>{@code rarityBonusPerTier}（默认 0.1）：稀有度系数 = {@code 1 + perTier × tier}</li>
 *   <li>{@code levelBonusPerLevel}（默认 0.2）：等级系数 = {@code 1 + perLevel × level}</li>
 * </ul>
 * 最终加成 = {@code floor(基础加成 × 稀有度系数 × 等级系数)}（服务端 {@code calculateTotalBonus}
 * 与客户端 tooltip 显示同口径）。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新。
 * 客户端镜像加载由 {@link com.rpgcraft.client.EquipmentTooltipEventHandler} 经
 * {@code IEquipmentSystem.applyBonusMultiplierConfig} 调用 {@link #loadFromJson}（与服务端同逻辑）。
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentBonusMultiplierConfig {

    /** 工程命名空间（与其它 rpg 配置保持一致）。 */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 配置文件 ID。 */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/equipment_bonus_multipliers.json");

    private static final Gson GSON = new Gson();

    /** 稀有度每级增幅默认值（保持原硬编码行为：1 + 0.1×tier）。 */
    static final double DEFAULT_RARITY_PER_TIER = 0.1;
    /** 等级每级增幅默认值（用户设计：每级 +0.2，即 1 + 0.2×level）。 */
    static final double DEFAULT_LEVEL_PER_LEVEL = 0.2;

    /**
     * 当前两个增幅常量。volatile 保证 reload 写入与读取线程可见性。
     * 包私有：供 {@link #applyConfig} 与同包单元测试写入。
     */
    static volatile double rarityBonusPerTier = DEFAULT_RARITY_PER_TIER;
    static volatile double levelBonusPerLevel = DEFAULT_LEVEL_PER_LEVEL;

    /** 稀有度每级增幅。 */
    public static double getRarityBonusPerTier() {
        return rarityBonusPerTier;
    }

    /** 等级每级增幅。 */
    public static double getLevelBonusPerLevel() {
        return levelBonusPerLevel;
    }

    /**
     * 稀有度系数 = {@code 1 + rarityBonusPerTier × tier}（tier=0 为 GRAY，系数 1.0×）。
     */
    public static double getRarityMultiplier(int tier) {
        return 1.0 + rarityBonusPerTier * tier;
    }

    /**
     * 等级系数 = {@code 1 + levelBonusPerLevel × level}（level=0 系数 1.0×，与无等级物品一致）。
     */
    public static double getLevelMultiplier(int level) {
        return 1.0 + levelBonusPerLevel * level;
    }

    /** 配置文件资源定位符（供客户端镜像加载通过 IEquipmentSystem 获取）。 */
    public static Identifier getConfigId() {
        return CONFIG_ID;
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/equipment_bonus_multipliers"),
                new SimplePreparableReloadListener<JsonObject>() {
                    @Override
                    protected @NonNull JsonObject prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        return readConfig(rm);
                    }

                    @Override
                    protected void apply(@NonNull JsonObject json, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                        applyConfig(json, false);
                    }
                });
    }

    /**
     * 从已解析的 JSON 应用配置（供服务端 reload 监听器与客户端镜像加载共用）。
     *
     * @param json   已解析的配置 JSON
     * @param client 是否客户端调用（仅用于日志区分）
     */
    public static void loadFromJson(JsonObject json, boolean client) {
        applyConfig(json, client);
    }

    private static JsonObject readConfig(ResourceManager rm) {
        var resource = rm.getResource(CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                EquipmentMod.LOGGER.warn("读取装备加成系数配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    private static void applyConfig(JsonObject json, boolean client) {
        if (json == null) json = new JsonObject();
        double rarity = parseNonNegativeDouble(json, "rarityBonusPerTier", DEFAULT_RARITY_PER_TIER);
        double level = parseNonNegativeDouble(json, "levelBonusPerLevel", DEFAULT_LEVEL_PER_LEVEL);
        rarityBonusPerTier = rarity;
        levelBonusPerLevel = level;
        EquipmentMod.LOGGER.info("装备加成系数配置已加载（{}）：稀有度每级 {}，等级每级 {}",
                client ? "客户端镜像" : "服务端", rarity, level);
    }

    /** 解析非负 double 字段；缺失或非法用默认值并 WARN。 */
    private static double parseNonNegativeDouble(JsonObject json, String field, double defaultValue) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive() || !json.get(field).getAsJsonPrimitive().isNumber()) {
            EquipmentMod.LOGGER.warn("装备加成系数配置：{} 缺失或非数字，用默认 {}", field, defaultValue);
            return defaultValue;
        }
        double v = json.get(field).getAsDouble();
        if (v < 0) {
            EquipmentMod.LOGGER.warn("装备加成系数配置：{} 为负 {}，用默认 {}", field, v, defaultValue);
            return defaultValue;
        }
        return v;
    }
}
