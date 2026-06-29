package com.rpgcraft.equipment;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.equipment.EquipmentRarity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 稀有度宝石铁砧锻造配置加载器
 * <p>
 * 加载 {@code data/rpgcraftcore/rpg/rarity_gemstone.json}：每个目标稀有度（不含 GRAY，GRAY 为最低级无升级目标）
 * 对应一条 {@link UpgradeRule}（消耗宝石数、成功率、失败消耗比例）。
 * <p>
 * 锻造规则：铁砧左槽放入已注册的可装备物品 + 右槽放入稀有度宝石，取出时按「目标稀有度 = 当前稀有度的下一级」
 * 查询 {@link UpgradeRule}，掷骰 {@code r < chance} 成功则升级一级；失败保留原武器并按 {@code failConsumeRate}
 * 退回部分宝石。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新。
 *
 * @see RarityForgeHandler
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class RarityGemstoneConfig {

    /** 工程命名空间（与其它 rpg 配置保持一致）。 */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 配置文件 ID。 */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/rarity_gemstone.json");

    /**
     * 配置文件资源定位符（供客户端镜像加载通过 {@code IEquipmentSystem.getGemstoneConfigId()} 获取）。
     */
    public static Identifier getConfigId() {
        return CONFIG_ID;
    }

    private static final Gson GSON = new Gson();

    /**
     * 升级规则：把一件装备从「当前稀有度」升到「目标稀有度」所需的代价与概率。
     *
     * @param gemCost         需消耗的稀有度宝石数量（成功全额、失败按 failConsumeRate 部分）
     * @param chance          成功率 [0,1]
     * @param failConsumeRate 失败时消耗的宝石比例 [0,1]（实际消耗 = ceil(gemCost × failConsumeRate)）
     */
    public record UpgradeRule(int gemCost, double chance, double failConsumeRate) {
        /** 单条规则的默认回退值（gemCost=0、chance=0 表示不可升级）。 */
        public static final UpgradeRule NONE = new UpgradeRule(0, 0.0, 0.0);
    }

    /**
     * 各目标稀有度的升级规则。volatile 保证 reload 写入与读取线程可见性。
     * 包私有：供 {@link #applyConfig} 与同包单元测试写入。
     */
    static volatile Map<EquipmentRarity, UpgradeRule> rules = Collections.emptyMap();

    /**
     * 查询升到某目标稀有度的规则。
     *
     * @param targetRarity 目标稀有度（升到此级所需的规则）
     * @return 升级规则；未配置返回 {@link UpgradeRule#NONE}
     */
    public static UpgradeRule getUpgradeRule(EquipmentRarity targetRarity) {
        return rules.getOrDefault(targetRarity, UpgradeRule.NONE);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/rarity_gemstone"),
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
                EquipmentMod.LOGGER.warn("读取稀有度宝石锻造配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    private static void applyConfig(JsonObject json) {
        applyConfig(json, false);
    }

    /**
     * 从已解析的 JSON 应用配置（供服务端 reload 监听器与客户端镜像加载共用）。
     * <p>
     * 客户端镜像：铁砧预览（{@link AnvilUpdateEvent}）在客户端也触发，需读取 gemCost 决定是否展示预览；
     * 但 {@link AddServerReloadListenersEvent} 仅服务端触发，故客户端需通过
     * {@link com.rpgcraft.client.EquipmentTooltipEventHandler} 镜像加载同一 JSON 调用本方法。
     *
     * @param json   已解析的配置 JSON
     * @param client 是否客户端调用（仅用于日志区分）
     */
    public static void loadFromJson(JsonObject json, boolean client) {
        applyConfig(json, client);
    }

    private static void applyConfig(JsonObject json, boolean client) {
        if (json == null) json = new JsonObject();
        Map<EquipmentRarity, UpgradeRule> parsed = new EnumMap<>(EquipmentRarity.class);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("//")) continue; // 容忍文档注释键
            EquipmentRarity target = EquipmentRarity.fromName(key);
            if (target == EquipmentRarity.GRAY) {
                EquipmentMod.LOGGER.warn("稀有度宝石锻造配置：GRAY 为最低级无升级目标，不应出现，已跳过");
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                EquipmentMod.LOGGER.warn("稀有度宝石锻造配置：{} 的值不是对象，已跳过", key);
                continue;
            }
            JsonObject ruleObj = entry.getValue().getAsJsonObject();
            int gemCost = parseIntField(ruleObj, "gemCost", key);
            double chance = parseDoubleField(ruleObj, "chance", key, 0.0, 1.0);
            double failConsumeRate = parseDoubleField(ruleObj, "failConsumeRate", key, 0.0, 1.0);
            if (gemCost < 0) {
                EquipmentMod.LOGGER.warn("稀有度宝石锻造配置：{} 的 gemCost {} 为负，已跳过", key, gemCost);
                continue;
            }
            parsed.put(target, new UpgradeRule(gemCost, chance, failConsumeRate));
        }
        rules = Collections.unmodifiableMap(parsed);
        EquipmentMod.LOGGER.info("稀有度宝石锻造配置已加载（{}）：{} 个目标等级",
                client ? "客户端镜像" : "服务端", parsed.size());
    }

    private static int parseIntField(JsonObject obj, String field, String key) {
        if (!obj.has(field) || !obj.get(field).isJsonPrimitive() || !obj.get(field).getAsJsonPrimitive().isNumber()) {
            EquipmentMod.LOGGER.warn("稀有度宝石锻造配置：{} 缺少 {} 或非数字，按 0 处理", key, field);
            return 0;
        }
        return obj.get(field).getAsInt();
    }

    private static double parseDoubleField(JsonObject obj, String field, String key, double min, double max) {
        if (!obj.has(field) || !obj.get(field).isJsonPrimitive() || !obj.get(field).getAsJsonPrimitive().isNumber()) {
            EquipmentMod.LOGGER.warn("稀有度宝石锻造配置：{} 缺少 {} 或非数字，按 {} 处理", key, field, min);
            return min;
        }
        double v = obj.get(field).getAsDouble();
        if (v < min || v > max) {
            EquipmentMod.LOGGER.warn("稀有度宝石锻造配置：{} 的 {} {} 越界 [{},{}]，按 {} 处理", key, field, v, min, max, min);
            return min;
        }
        return v;
    }
}
