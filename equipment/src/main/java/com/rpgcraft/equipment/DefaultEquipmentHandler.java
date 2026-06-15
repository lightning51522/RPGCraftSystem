package com.rpgcraft.equipment;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.AttributeModifier;
import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.equipment.api.IEquipmentRegistry;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.equipment.api.IEquipmentHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link IEquipmentHandler} 的默认实现
 * <p>
 * 监听装备变化，通过 {@link IEquipmentRegistry} 查询加成数据，
 * 使用属性修饰符（{@link IAttributeModifier}）将装备加成应用到属性管线中。
 * <p>
 * <b>修饰符模式（替代旧版差分机制）：</b>
 * <ol>
 *   <li>每次装备变化时，先移除所有旧装备修饰符</li>
 *   <li>重新计算所有装备的总加成</li>
 *   <li>为每个有加成的属性添加新的 ADDITION 修饰符</li>
 *   <li>属性值由管线自动重算</li>
 *   <li>将新总加成存入追踪附件</li>
 * </ol>
 * <p>
 * 修饰符来源标识符格式：{@code "rpgcraftequipment:<attrId>"}，
 * 每个属性只有一个装备来源修饰符（聚合所有装备槽的总加成）。
 */
public class DefaultEquipmentHandler implements IEquipmentHandler {

    /** 装备修饰符的来源前缀 */
    private static final String MODIFIER_PREFIX = "rpgcraftequipment";

    protected final IEquipmentRegistry registry;

    public DefaultEquipmentHandler(IEquipmentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 生成装备修饰符的来源标识符
     *
     * @param attrId 属性标识符
     * @return 修饰符来源标识符
     */
    private static Identifier modifierSourceId(Identifier attrId) {
        return Identifier.fromNamespaceAndPath(MODIFIER_PREFIX, "bonus_" + attrId.getNamespace() + "_" + attrId.getPath());
    }

    /**
     * 计算玩家当前所有装备的总加成
     * <p>
     * 遍历所有装备槽位（头盔、胸甲、护腿、靴子、主手、副手），
     * 查询注册中心中每个物品的属性加成，按属性 ID 累加。
     * 空槽位和未注册加成的物品会被跳过。
     *
     * @param player 目标玩家
     * @return 属性ID → 累计加成值的映射（不含无加成的属性）
     */
    @Override
    public Map<Identifier, EquipmentBonus> calculateTotalBonus(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> total = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            // 护甲类物品在手持槽位（主手/副手）时不应用加成，必须在装备栏中才生效
            if (slot.getType() == EquipmentSlot.Type.HAND) {
                Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    continue;
                }
            }

            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            Optional<Map<Identifier, EquipmentBonus>> bonuses = registry.getBonuses(itemId);
            if (bonuses.isEmpty()) continue;
            for (Map.Entry<Identifier, EquipmentBonus> entry : bonuses.get().entrySet()) {
                total.merge(entry.getKey(), entry.getValue(), EquipmentBonus::add);
            }
        }
        return total;
    }

    /**
     * 处理装备变化事件
     * <p>
     * 使用修饰符模式：先移除旧修饰符，再根据新总加成添加新修饰符。
     * 属性值由管线自动重算，无需手动计算差值。
     *
     * @param player 装备发生变化的玩家（已由调用方确认为 ServerPlayer）
     */
    @Override
    public void onEquipmentChange(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> newTotal = calculateTotalBonus(player);

        // 读取旧的追踪数据
        Map<String, EquipmentBonus> oldStorage = player.getData(EquipmentData.EQUIPMENT_BONUS.get());
        Map<Identifier, EquipmentBonus> oldTotal = storageToMap(oldStorage);

        // 应用修饰符变化
        applyModifiers(player, oldTotal, newTotal);

        // 更新追踪附件
        player.setData(EquipmentData.EQUIPMENT_BONUS.get(), mapToStorage(newTotal));

        // 标记属性快照脏（装备变化影响属性值）
        AttributeSnapshotManager.markDirty(player);
    }

    /**
     * 在重生或登录后恢复装备加成追踪数据并重新应用修饰符
     * <p>
     * 从当前装备重新计算总加成，移除所有旧装备修饰符后添加新修饰符，
     * 并更新追踪附件。
     *
     * @param player 目标玩家
     */
    @Override
    public void restoreBonusTracking(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> total = calculateTotalBonus(player);

        // 移除所有旧装备修饰符
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            IAttribute attr = player.getData(entry.getSupplier());
            attr.removeModifier(modifierSourceId(entry.getId()));
        }

        // 添加新的装备修饰符
        for (Map.Entry<Identifier, EquipmentBonus> bonusEntry : total.entrySet()) {
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(bonusEntry.getKey());
            if (attrEntry == null) continue;

            IAttribute attr = player.getData(attrEntry.getSupplier());
            attr.addModifier(AttributeModifier.of(
                    modifierSourceId(bonusEntry.getKey()),
                    Operation.ADDITION,
                    bonusEntry.getValue().value()
            ));
        }

        // 资源型属性（如生命）装备影响上限：添加到 baseMaxValue
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            if (entry.equipmentAffectsMax()) {
                EquipmentBonus bonus = total.getOrDefault(entry.getId(), EquipmentBonus.ZERO);
                if (bonus.value() != 0) {
                    IAttribute attr = player.getData(entry.getSupplier());
                    // 上限修饰符用独立来源
                    attr.addModifier(AttributeModifier.of(
                            Identifier.fromNamespaceAndPath(MODIFIER_PREFIX, "max_" + entry.getId().getNamespace() + "_" + entry.getId().getPath()),
                            Operation.ADDITION,
                            bonus.value()
                    ));
                }
            }
        }

        player.setData(EquipmentData.EQUIPMENT_BONUS.get(), mapToStorage(total));
    }

    /**
     * 使用修饰符模式应用装备加成变化
     * <p>
     * 逐属性处理：
     * <ul>
     *   <li>移除旧修饰符</li>
     *   <li>添加新修饰符（如有加成）</li>
     *   <li>资源型属性额外处理上限修饰符</li>
     * </ul>
     *
     * @param player   目标玩家
     * @param oldTotal 变更前的总加成映射
     * @param newTotal 变更后的总加成映射
     */
    protected void applyModifiers(ServerPlayer player,
                                  Map<Identifier, EquipmentBonus> oldTotal,
                                  Map<Identifier, EquipmentBonus> newTotal) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            EquipmentBonus oldBonus = oldTotal.getOrDefault(entry.getId(), EquipmentBonus.ZERO);
            EquipmentBonus newBonus = newTotal.getOrDefault(entry.getId(), EquipmentBonus.ZERO);

            IAttribute attr = player.getData(entry.getSupplier());

            // 移除旧的值修饰符
            Identifier valueSourceId = modifierSourceId(entry.getId());
            attr.removeModifier(valueSourceId);

            // 添加新的值修饰符
            if (newBonus.value() != 0) {
                attr.addModifier(AttributeModifier.of(valueSourceId, Operation.ADDITION, newBonus.value()));
            }

            // 资源型属性：额外处理上限修饰符
            if (entry.equipmentAffectsMax()) {
                Identifier maxSourceId = Identifier.fromNamespaceAndPath(MODIFIER_PREFIX,
                        "max_" + entry.getId().getNamespace() + "_" + entry.getId().getPath());
                attr.removeModifier(maxSourceId);

                if (newBonus.value() != 0) {
                    attr.addModifier(AttributeModifier.of(maxSourceId, Operation.ADDITION, newBonus.value()));
                }
            }

            // 脱装备导致生命为 0 时保留 1 点防止死亡
            if (entry.getId().equals(AttributeManager.LIFE_ID) && attr.getValue() < 1) {
                attr.setValue(1);
            }

            SyncPlayerAttributePacket.sendToClient(player, entry.getId(), (EntityAttribute) attr);
        }
    }

    /**
     * 将字符串键的存储格式转换为 Identifier 键的计算格式
     */
    static Map<Identifier, EquipmentBonus> storageToMap(Map<String, EquipmentBonus> storage) {
        Map<Identifier, EquipmentBonus> result = new HashMap<>();
        for (Map.Entry<String, EquipmentBonus> e : storage.entrySet()) {
            result.put(Identifier.parse(e.getKey()), e.getValue());
        }
        return result;
    }

    /**
     * 将 Identifier 键的计算格式转换为字符串键的存储格式
     */
    static Map<String, EquipmentBonus> mapToStorage(Map<Identifier, EquipmentBonus> map) {
        Map<String, EquipmentBonus> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, EquipmentBonus> e : map.entrySet()) {
            result.put(e.getKey().toString(), e.getValue());
        }
        return result;
    }
}
