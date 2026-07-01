package com.rpgcraft.gemstone;

import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.equipment.GemInstance;
import com.rpgcraft.core.equipment.RPGComponents;
import com.rpgcraft.core.equipment.api.EquipmentBonusCoordinator;
import com.rpgcraft.core.equipment.api.IEquipmentBonusContributor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 镶嵌宝石加成贡献者
 * <p>
 * 实现 {@link IEquipmentBonusContributor}：为镶嵌了宝石的装备贡献属性词条加成。
 * 由 {@link GemstoneManager#init()} 注册到 {@link EquipmentBonusCoordinator}，装备模块的
 * {@code DefaultEquipmentHandler.calculateTotalBonus} 会聚合本贡献者的返回值 —— equipment 模块
 * 对 gemstone 模块零编译期依赖。
 * <p>
 * <b>逻辑</b>：读取装备的 {@code EQUIPMENT_SOCKET} 组件（镶嵌的那颗宝石），遍历其词条。
 * 词条 ID 直接就是 RPG 属性 ID（单层映射），属性词条按宝石稀有度查 {@link SocketGemConfig}
 * 数值表（专属表缺失回退 {@code _default} 默认表）累加到对应属性的 {@link EquipmentBonus}。
 * 特效词条不在此处理（由 {@link GemCombatEventListener} 接入战斗事件）。
 * <p>
 * <b>缩放</b>：宝石词条数值固定（由宝石稀有度查表），不再乘装备稀有度/等级缩放系数 ——
 * 「同名词条在不同稀有度宝石上数值不同」已由配置的 per-rarity values 体现。
 *
 * @see EquipmentBonusCoordinator 装备加成贡献者协调器（聚合入口）
 */
public class SocketGemBonusContributor implements IEquipmentBonusContributor {

    @Override
    public String getContributorId() {
        return "rpgcraftgemstone:socket_gem_bonus";
    }

    @Override
    public Map<Identifier, EquipmentBonus> contribute(ItemStack stack) {
        GemInstance gem = stack.get(RPGComponents.EQUIPMENT_SOCKET.get());
        if (gem == null) {
            // 未镶嵌
            return Map.of();
        }
        Map<Identifier, EquipmentBonus> total = new HashMap<>();
        for (Identifier affixId : gem.affixIds()) {
            // 词条 ID 直接就是 attributeId（单层映射）。非可作词条属性（含特效词条）跳过。
            if (!SocketGemConfig.isAttribute(affixId)) continue;
            int value = SocketGemConfig.getAttributeValue(affixId, gem.rarity());
            if (value == 0) continue;
            total.merge(affixId, new EquipmentBonus(value), EquipmentBonus::add);
        }
        return total;
    }
}
