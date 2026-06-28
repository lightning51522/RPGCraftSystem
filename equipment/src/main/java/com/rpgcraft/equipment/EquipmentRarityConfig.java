package com.rpgcraft.equipment;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.equipment.EquipmentRarity;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 装备稀有度随机生成概率配置加载器
 * <p>
 * 加载 {@code data/rpgcraftcore/rpg/equipment_rarity.json}：每个非 GRAY 稀有度等级对应一个
 * 「独立判定」概率。装备生成时（制作 / 进入世界）按 ordinal 从高到低遍历，首个 {@code r < 概率}
 * 命中的等级即为该件装备稀有度；全部未命中则保持 {@link EquipmentRarity#GRAY}。
 * <p>
 * 监听 {@link AddServerReloadListenersEvent} 支持 {@code /reload} 热更新。
 * <p>
 * 注意：概率之和不必等于 1。每级是独立判定，不是累乘链式。
 *
 * @see EquipmentRarityRoller
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentRarityConfig {

    /** 职业命名空间（与其它 rpg 配置保持一致） */
    public static final String NAMESPACE = "rpgcraftcore";
    /** 概率配置文件 ID */
    private static final Identifier CONFIG_ID =
            Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/equipment_rarity.json");

    private static final Gson GSON = new Gson();

    /**
     * 各等级概率（不含 GRAY，GRAY 为默认兜底）。volatile 保证 reload 写入与读取线程可见性。
     * 包私有：供 {@link #applyConfig} 与同包单元测试写入。
     */
    static volatile Map<EquipmentRarity, Double> probabilities = Collections.emptyMap();

    /**
     * 查询某稀有度等级的随机生成概率（GRAY 固定为 0，由兜底保证）。
     *
     * @param rarity 稀有度等级
     * @return 概率 [0,1]；未配置返回 0
     */
    public static double getProbability(EquipmentRarity rarity) {
        if (rarity == EquipmentRarity.GRAY) return 0.0;
        return probabilities.getOrDefault(rarity, 0.0);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(
                Identifier.fromNamespaceAndPath(NAMESPACE, "rpg/equipment_rarity"),
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
                EquipmentMod.LOGGER.warn("读取装备稀有度概率配置失败: {} - {}", CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    private static void applyConfig(JsonObject json) {
        if (json == null) json = new JsonObject();
        Map<EquipmentRarity, Double> parsed = new EnumMap<>(EquipmentRarity.class);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("//")) continue; // 容忍文档注释键
            EquipmentRarity rarity = EquipmentRarity.fromName(key);
            if (rarity == EquipmentRarity.GRAY) {
                EquipmentMod.LOGGER.warn("装备稀有度概率配置：GRAY 为默认兜底等级，不应出现在概率表中，已跳过");
                continue;
            }
            if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) {
                EquipmentMod.LOGGER.warn("装备稀有度概率配置：{} 的值不是数字，已跳过", key);
                continue;
            }
            double prob = entry.getValue().getAsDouble();
            if (prob < 0 || prob > 1) {
                EquipmentMod.LOGGER.warn("装备稀有度概率配置：{} 的概率 {} 越界 [0,1]，已跳过", key, prob);
                continue;
            }
            parsed.put(rarity, prob);
        }
        probabilities = Collections.unmodifiableMap(parsed);
        EquipmentMod.LOGGER.info("装备稀有度概率配置已加载：{} 个非 GRAY 等级", parsed.size());
    }

    /**
     * 按配置的概率随机生成一个稀有度等级。
     * <p>
     * 算法：取 {@code r = random.nextDouble() ∈ [0,1)}，按 ordinal 从高到低遍历，首个
     * {@code r < prob(等级)} 命中的等级即为结果；全部未命中返回 {@link EquipmentRarity#GRAY}。
     * 从高到低遍历使顶级小概率仍可能命中，且同一 r 只命中一个等级。
     *
     * @param random 随机源
     * @return 随机生成的稀有度
     */
    public static EquipmentRarity rollRarity(RandomSource random) {
        double r = random.nextDouble();
        // 从高到低遍历（RAINBOW → WHITE），首个命中即返回
        EquipmentRarity[] tiers = EquipmentRarity.values();
        for (int i = tiers.length - 1; i > 0; i--) { // 跳过 i==0 (GRAY)
            EquipmentRarity tier = tiers[i];
            if (r < probabilities.getOrDefault(tier, 0.0)) {
                return tier;
            }
        }
        return EquipmentRarity.GRAY;
    }
}
