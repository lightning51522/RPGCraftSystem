package com.rpgcraft.core.equipment.api;

import com.google.gson.JsonObject;
import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.equipment.EquipmentRarity;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * 装备属性加成注册中心
 * <p>
 * 提供装备属性加成的注册和查询能力。
 * 默认实现为 {@link com.rpgcraft.core.equipment.DefaultEquipmentRegistry}。
 * <p>
 * 其他模组通过 {@link IEquipmentProvider#registerEquipment(IEquipmentRegistry)}
 * 获取注册中心实例并注册自定义装备加成。
 */
public interface IEquipmentRegistry {

    /**
     * 注册物品的属性加成
     *
     * @param itemId  物品标识符（如 minecraft:diamond_sword）
     * @param bonuses 属性加成映射（属性ID → 加成值）
     */
    void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses);

    /**
     * 注册物品的属性加成和稀有度
     *
     * @param itemId  物品标识符
     * @param bonuses 属性加成映射
     * @param rarity  装备稀有度
     */
    void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses, EquipmentRarity rarity);

    /**
     * 查询物品的属性加成
     *
     * @param itemId 物品标识符
     * @return 属性加成映射，未找到返回空 Optional
     */
    Optional<Map<Identifier, EquipmentBonus>> getBonuses(Identifier itemId);

    /**
     * 查询物品的稀有度
     *
     * @param itemId 物品标识符
     * @return 稀有度，未找到返回 {@link EquipmentRarity#COMMON}
     */
    EquipmentRarity getRarity(Identifier itemId);

    /**
     * 查询物品的攻击伤害类型
     *
     * @param itemId 物品标识符
     * @return 攻击类型，未找到返回 {@link AttackType#PHYSICAL}
     */
    AttackType getAttackType(Identifier itemId);

    /**
     * 从 JSON 配置加载装备加成数据和稀有度
     * <p>
     * 用于资源重载监听器（服务端和客户端）调用，
     * 支持 {@code /reload} 热更新装备配置。
     *
     * @param json 装备属性配置 JSON
     */
    void loadFromJson(JsonObject json);
}
