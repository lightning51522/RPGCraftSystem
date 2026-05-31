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
 * 管理装备属性加成的注册和查询。支持两种数据来源：
 * <ul>
 *   <li>JSON 配置文件（通过 {@link #loadFromJson(JsonObject)} 加载）</li>
 *   <li>编程式注册（通过 {@link #register(Identifier, Map)}）</li>
 * </ul>
 */
public class DefaultEquipmentRegistry implements IEquipmentRegistry {

    public static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/equipment_attributes.json");

    private volatile Map<Identifier, Map<Identifier, EquipmentBonus>> configMap = Collections.emptyMap();

    /**
     * 从 JSON 配置加载装备加成数据
     * <p>
     * 解析 JSON 并替换内部缓存，供服务端和客户端重载监听器共用。
     *
     * @param json 装备属性配置 JSON
     */
    public void loadFromJson(JsonObject json) {
        Map<Identifier, Map<Identifier, EquipmentBonus>> newMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> itemEntry : json.entrySet()) {
            try {
                Identifier itemId = Identifier.parse(itemEntry.getKey());
                JsonObject attrObj = itemEntry.getValue().getAsJsonObject();
                Map<Identifier, EquipmentBonus> bonuses = new HashMap<>();
                for (Map.Entry<String, JsonElement> attrEntry : attrObj.entrySet()) {
                    try {
                        Identifier attrId = Identifier.parse(attrEntry.getKey());
                        bonuses.put(attrId, new EquipmentBonus(attrEntry.getValue().getAsInt()));
                    } catch (Exception e) {
                        RPGCraftCore.LOGGER.warn("解析装备属性加成失败: {} > {}: {}", itemEntry.getKey(), attrEntry.getKey(), e.getMessage());
                    }
                }
                newMap.put(itemId, Collections.unmodifiableMap(bonuses));
            } catch (Exception e) {
                RPGCraftCore.LOGGER.warn("解析装备配置失败: {} - {}", itemEntry.getKey(), e.getMessage());
            }
        }
        configMap = Collections.unmodifiableMap(newMap);
        RPGCraftCore.LOGGER.info("已加载 {} 种装备的属性加成配置", newMap.size());
    }

    @Override
    public void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses) {
        Map<Identifier, Map<Identifier, EquipmentBonus>> newMap = new HashMap<>(configMap);
        newMap.put(itemId, Collections.unmodifiableMap(new HashMap<>(bonuses)));
        configMap = Collections.unmodifiableMap(newMap);
    }

    @Override
    public Optional<Map<Identifier, EquipmentBonus>> getBonuses(Identifier itemId) {
        return Optional.ofNullable(configMap.get(itemId));
    }
}
