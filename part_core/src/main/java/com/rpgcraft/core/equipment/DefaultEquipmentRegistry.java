package com.rpgcraft.core.equipment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.equipment.api.IEquipmentRegistry;
import net.minecraft.resources.Identifier;

import java.util.*;

/**
 * {@link IEquipmentRegistry} 的默认实现
 * <p>
 * 管理装备属性加成和稀有度的注册与查询。支持两种数据来源：
 * <ul>
 *   <li>JSON 配置文件（通过 {@link #loadFromJson(JsonObject)} 加载）</li>
 *   <li>编程式注册（通过 {@link #register}）</li>
 * </ul>
 */
public class DefaultEquipmentRegistry implements IEquipmentRegistry {

    public static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/equipment_attributes.json");

    private static final String RARITY_KEY = "rarity";

    private volatile Map<Identifier, Map<Identifier, EquipmentBonus>> configMap = Collections.emptyMap();
    private volatile Map<Identifier, EquipmentRarity> rarityMap = Collections.emptyMap();

    /**
     * 从 JSON 配置加载装备加成数据和稀有度
     * <p>
     * JSON 中每项可包含 {@code "rarity"} 字段指定稀有度（如 "rare"），
     * 其余字段为属性加成（如 {@code "rpgcraftcore:strength": 10}）。
     *
     * @param json 装备属性配置 JSON
     */
    public void loadFromJson(JsonObject json) {
        Map<Identifier, Map<Identifier, EquipmentBonus>> newBonusMap = new HashMap<>();
        Map<Identifier, EquipmentRarity> newRarityMap = new HashMap<>();

        for (Map.Entry<String, JsonElement> itemEntry : json.entrySet()) {
            try {
                Identifier itemId = Identifier.parse(itemEntry.getKey());
                JsonObject attrObj = itemEntry.getValue().getAsJsonObject();

                // 解析稀有度
                if (attrObj.has(RARITY_KEY)) {
                    EquipmentRarity rarity = EquipmentRarity.fromName(attrObj.get(RARITY_KEY).getAsString());
                    newRarityMap.put(itemId, rarity);
                }

                // 解析属性加成（跳过 "rarity" 键）
                Map<Identifier, EquipmentBonus> bonuses = new HashMap<>();
                for (Map.Entry<String, JsonElement> attrEntry : attrObj.entrySet()) {
                    if (attrEntry.getKey().equals(RARITY_KEY)) continue;
                    try {
                        Identifier attrId = Identifier.parse(attrEntry.getKey());
                        bonuses.put(attrId, new EquipmentBonus(attrEntry.getValue().getAsInt()));
                    } catch (Exception e) {
                        RPGCraftCore.LOGGER.warn("解析装备属性加成失败: {} > {}: {}", itemEntry.getKey(), attrEntry.getKey(), e.getMessage());
                    }
                }
                newBonusMap.put(itemId, Collections.unmodifiableMap(bonuses));
            } catch (Exception e) {
                RPGCraftCore.LOGGER.warn("解析装备配置失败: {} - {}", itemEntry.getKey(), e.getMessage());
            }
        }
        configMap = Collections.unmodifiableMap(newBonusMap);
        rarityMap = Collections.unmodifiableMap(newRarityMap);
        RPGCraftCore.LOGGER.info("已加载 {} 种装备的属性加成配置", newBonusMap.size());
    }

    @Override
    public void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses) {
        register(itemId, bonuses, EquipmentRarity.COMMON);
    }

    @Override
    public void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses, EquipmentRarity rarity) {
        Map<Identifier, Map<Identifier, EquipmentBonus>> newBonusMap = new HashMap<>(configMap);
        newBonusMap.put(itemId, Collections.unmodifiableMap(new HashMap<>(bonuses)));
        configMap = Collections.unmodifiableMap(newBonusMap);

        if (rarity != EquipmentRarity.COMMON) {
            Map<Identifier, EquipmentRarity> newRarityMap = new HashMap<>(rarityMap);
            newRarityMap.put(itemId, rarity);
            rarityMap = Collections.unmodifiableMap(newRarityMap);
        }
    }

    @Override
    public Optional<Map<Identifier, EquipmentBonus>> getBonuses(Identifier itemId) {
        return Optional.ofNullable(configMap.get(itemId));
    }

    @Override
    public EquipmentRarity getRarity(Identifier itemId) {
        return rarityMap.getOrDefault(itemId, EquipmentRarity.COMMON);
    }
}
