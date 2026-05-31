package com.rpgcraft.core.equipment;

import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.equipment.api.IEquipmentHandler;
import com.rpgcraft.core.equipment.api.IEquipmentRegistry;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link IEquipmentHandler} 的默认实现
 * <p>
 * 监听装备变化，通过 {@link IEquipmentRegistry} 查询加成数据，
 * 计算新旧差值并应用到玩家属性上。
 */
public class DefaultEquipmentHandler implements IEquipmentHandler {

    protected final IEquipmentRegistry registry;

    public DefaultEquipmentHandler(IEquipmentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Map<Identifier, EquipmentBonus> calculateTotalBonus(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> total = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            Optional<Map<Identifier, EquipmentBonus>> bonuses = registry.getBonuses(itemId);
            if (bonuses.isEmpty()) continue;
            for (Map.Entry<Identifier, EquipmentBonus> entry : bonuses.get().entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), EquipmentBonus::add);
            }
        }
        return total;
    }

    @Override
    public void onEquipmentChange(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> newTotal = calculateTotalBonus(player);

        Map<String, EquipmentBonus> oldStorage = player.getData(EquipmentData.EQUIPMENT_BONUS.get());
        Map<Identifier, EquipmentBonus> oldTotal = storageToMap(oldStorage);

        applyBonusDiff(player, oldTotal, newTotal);

        player.setData(EquipmentData.EQUIPMENT_BONUS.get(), mapToStorage(newTotal));
    }

    @Override
    public void restoreBonusTracking(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> total = calculateTotalBonus(player);
        player.setData(EquipmentData.EQUIPMENT_BONUS.get(), mapToStorage(total));
    }

    /**
     * 将加成差值应用到玩家属性
     */
    protected void applyBonusDiff(ServerPlayer player,
                                  Map<Identifier, EquipmentBonus> oldTotal,
                                  Map<Identifier, EquipmentBonus> newTotal) {
        for (IAttributeEntry entry : GenericEntityData.getRegistry().getAllEntries()) {
            EquipmentBonus oldBonus = oldTotal.getOrDefault(entry.getId(), EquipmentBonus.ZERO);
            EquipmentBonus newBonus = newTotal.getOrDefault(entry.getId(), EquipmentBonus.ZERO);

            int diff = newBonus.value() - oldBonus.value();

            if (diff == 0) continue;

            IAttribute attr = player.getData(entry.getSupplier());

            if (entry.equipmentAffectsMax()) {
                attr.setMaxValue(Math.clamp(attr.getMaxValue() + diff, 0, Integer.MAX_VALUE));
            }
            attr.setValue(attr.getValue() + diff);

            // 脱装备减生命时至少保留1点，防止玩家死亡
            if (entry.getId().equals(GenericEntityData.LIFE_ID) && attr.getValue() < 1) {
                attr.setValue(1);
            }

            SyncPlayerAttributePacket.sendToClient(player, entry.getId(), (EntityAttribute) attr);
        }
    }

    static Map<Identifier, EquipmentBonus> storageToMap(Map<String, EquipmentBonus> storage) {
        Map<Identifier, EquipmentBonus> result = new HashMap<>();
        for (Map.Entry<String, EquipmentBonus> e : storage.entrySet()) {
            result.put(Identifier.parse(e.getKey()), e.getValue());
        }
        return result;
    }

    static Map<String, EquipmentBonus> mapToStorage(Map<Identifier, EquipmentBonus> map) {
        Map<String, EquipmentBonus> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, EquipmentBonus> e : map.entrySet()) {
            result.put(e.getKey().toString(), e.getValue());
        }
        return result;
    }
}
