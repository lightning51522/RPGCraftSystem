package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.attribute.AttackType;
import com.rpgcraft.core.equipment.EquipmentBonus;
import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.api.IEquipmentRegistry;
import com.rpgcraft.core.registry.IEquipmentSystem;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * {@link IEquipmentSystem} 的 no-op 兜底实现。
 * <p>
 * 无 equipment 模块时由 core 预填充：加成恢复为 no-op、注册中心为空注册表、配置 ID 为 null。
 * 首次调用记录一次 WARN，提示 equipment 模块未加载。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getEquipmentSystem()
 */
public final class NoOpEquipmentSystem implements IEquipmentSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("RPGCraftCore/NoOpEquipmentSystem");
    private static volatile boolean warned = false;
    private static final IEquipmentRegistry EMPTY_REGISTRY = new EmptyEquipmentRegistry();

    private static void warnOnce() {
        if (!warned) {
            synchronized (NoOpEquipmentSystem.class) {
                if (!warned) {
                    LOGGER.warn("equipment 模块未加载，IEquipmentSystem 使用 no-op 兜底（无装备加成）");
                    warned = true;
                }
            }
        }
    }

    @Override
    public void restoreBonusTracking(ServerPlayer player) {
        warnOnce();
    }

    @Override
    public IEquipmentRegistry getRegistry() {
        warnOnce();
        return EMPTY_REGISTRY;
    }

    @Override
    public Identifier getConfigId() {
        warnOnce();
        return null;
    }

    @Override
    public Identifier getGemstoneConfigId() {
        warnOnce();
        return null;
    }

    @Override
    public void applyGemstoneConfig(JsonObject json) {
        warnOnce();
    }

    /** 空装备注册表：所有查询返回空/默认。 */
    private static final class EmptyEquipmentRegistry implements IEquipmentRegistry {
        @Override
        public void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses) {
        }

        @Override
        public void register(Identifier itemId, Map<Identifier, EquipmentBonus> bonuses, EquipmentRarity rarity) {
        }

        @Override
        public Optional<Map<Identifier, EquipmentBonus>> getBonuses(Identifier itemId) {
            return Optional.empty();
        }

        @Override
        public EquipmentRarity getRarity(Identifier itemId) {
            return EquipmentRarity.GRAY;
        }

        @Override
        public AttackType getAttackType(Identifier itemId) {
            return AttackType.PHYSICAL;
        }

        @Override
        public void loadFromJson(JsonObject json) {
        }
    }
}
