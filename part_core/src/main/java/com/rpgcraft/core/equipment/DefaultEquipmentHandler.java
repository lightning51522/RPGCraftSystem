package com.rpgcraft.core.equipment;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
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
 * <p>
 * <b>核心差分机制：</b>
 * <ol>
 *   <li>每次装备变化时，重新计算所有装备的总加成（{@link #calculateTotalBonus}）</li>
 *   <li>从 {@link EquipmentData#EQUIPMENT_BONUS} 附件读取上次的总加成作为"旧值"</li>
 *   <li>计算每个属性的差值（diff = 新加成 - 旧加成）</li>
 *   <li>通过 {@link #applyBonusDiff} 将差值应用到玩家属性，并同步到客户端</li>
 *   <li>将新总加成存回附件，作为下次变化时的"旧值"</li>
 * </ol>
 */
public class DefaultEquipmentHandler implements IEquipmentHandler {

    protected final IEquipmentRegistry registry;

    public DefaultEquipmentHandler(IEquipmentRegistry registry) {
        this.registry = registry;
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
     * 计算新的总加成，与旧的追踪数据对比，通过 {@link #applyBonusDiff} 应用差值，
     * 最后将新总加成存入 {@link EquipmentData#EQUIPMENT_BONUS} 附件供下次使用。
     *
     * @param player 装备发生变化的玩家（已由调用方确认为 ServerPlayer）
     */
    @Override
    public void onEquipmentChange(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> newTotal = calculateTotalBonus(player);

        Map<String, EquipmentBonus> oldStorage = player.getData(EquipmentData.EQUIPMENT_BONUS.get());
        Map<Identifier, EquipmentBonus> oldTotal = storageToMap(oldStorage);

        applyBonusDiff(player, oldTotal, newTotal);

        player.setData(EquipmentData.EQUIPMENT_BONUS.get(), mapToStorage(newTotal));
    }

    /**
     * 在重生或登录后恢复装备加成追踪数据
     * <p>
     * 不修改任何属性值，仅从当前装备重新计算总加成并存入追踪附件。
     * 用于在以下场景中初始化追踪数据：
     * <ul>
     *   <li>玩家登录：存档中的属性值已包含装备加成，只需重建追踪映射</li>
     *   <li>玩家重生：克隆后的新实体属性值已由快照恢复，需重建追踪映射</li>
     * </ul>
     *
     * @param player 目标玩家
     */
    @Override
    public void restoreBonusTracking(ServerPlayer player) {
        Map<Identifier, EquipmentBonus> total = calculateTotalBonus(player);
        player.setData(EquipmentData.EQUIPMENT_BONUS.get(), mapToStorage(total));
    }

    /**
     * 将加成差值应用到玩家属性
     * <p>
     * 根据属性的 {@link IAttributeEntry#equipmentAffectsMax()} 标志采用不同的应用策略：
     * <ul>
     *   <li><b>资源型属性</b>（如生命，{@code equipmentAffectsMax=true}）：
     *       装备只影响上限值，不直接修改当前值。
     *       穿上装备时上限增加，玩家需自行回复到新上限；
     *       脱下装备时上限减少，若当前值超过新上限则自动钳制。
     *       此设计避免了"脱下再穿上装备来回血"的漏洞。</li>
     *   <li><b>能力型属性</b>（如力量、防御，{@code equipmentAffectsMax=false}）：
     *       装备直接改变当前值。</li>
     * </ul>
     *
     * @param player   目标玩家
     * @param oldTotal 变更前的总加成映射
     * @param newTotal 变更后的总加成映射
     */
    protected void applyBonusDiff(ServerPlayer player,
                                  Map<Identifier, EquipmentBonus> oldTotal,
                                  Map<Identifier, EquipmentBonus> newTotal) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            EquipmentBonus oldBonus = oldTotal.getOrDefault(entry.getId(), EquipmentBonus.ZERO);
            EquipmentBonus newBonus = newTotal.getOrDefault(entry.getId(), EquipmentBonus.ZERO);

            int diff = newBonus.value() - oldBonus.value();

            if (diff == 0) continue;

            IAttribute attr = player.getData(entry.getSupplier());

            if (entry.equipmentAffectsMax()) {
                // 资源型属性（如生命）：装备只影响上限，不影响当前值
                // 穿上装备：上限增加，当前值不变 → 玩家需要自行回复到新上限
                // 脱下装备：上限减少，若当前值超过新上限则钳制到新上限
                int newMax = Math.clamp(attr.getMaxValue() + diff, 0, Integer.MAX_VALUE);
                attr.setMaxValue(newMax);
                // setMaxValue 的钳制副作用在此处恰好是正确行为
            } else {
                // 能力型属性（如力量、防御）：装备直接影响当前值
                attr.setValue(Math.clamp(attr.getValue() + diff, 0, attr.getMaxValue()));
            }

            // 脱装备导致上限降低后，若生命被钳制到 0 则保留 1 点防止死亡
            if (entry.getId().equals(AttributeManager.LIFE_ID) && attr.getValue() < 1) {
                attr.setValue(1);
            }

            SyncPlayerAttributePacket.sendToClient(player, entry.getId(), (EntityAttribute) attr);
        }
    }

    /**
     * 将字符串键的存储格式转换为 Identifier 键的计算格式
     * <p>
     * 用于从 {@link EquipmentData#EQUIPMENT_BONUS} 附件读取旧加成数据时，
     * 将存储用的字符串键（{@code "rpgcraftcore:strength"}）解析为 {@link Identifier}。
     *
     * @param storage 附件中存储的字符串键映射
     * @return Identifier 键的映射
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
     * <p>
     * 用于将计算后的总加成写回 {@link EquipmentData#EQUIPMENT_BONUS} 附件时，
     * 将 {@link Identifier} 转为字符串（{@code "rpgcraftcore:strength"}）。
     * 使用 {@link LinkedHashMap} 保持插入顺序的确定性。
     *
     * @param map Identifier 键的映射
     * @return 字符串键的映射（用于附件存储）
     */
    static Map<String, EquipmentBonus> mapToStorage(Map<Identifier, EquipmentBonus> map) {
        Map<String, EquipmentBonus> result = new LinkedHashMap<>();
        for (Map.Entry<Identifier, EquipmentBonus> e : map.entrySet()) {
            result.put(e.getKey().toString(), e.getValue());
        }
        return result;
    }
}
