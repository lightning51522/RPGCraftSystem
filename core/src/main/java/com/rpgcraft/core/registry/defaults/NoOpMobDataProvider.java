package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.level.api.IMobAttributeScaler;
import com.rpgcraft.core.registry.IMobDataProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * {@link IMobDataProvider} 的 no-op 兜底实现。
 * <p>
 * 无 leveling 模块时由 core 预填充：怪物等级数据为未设置（level=0）、缩放器为恒等缩放、
 * 属性配置返回 empty、刷新分布返回 null。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getMobDataProvider()
 */
public final class NoOpMobDataProvider implements IMobDataProvider {

    private static final IMobAttributeScaler IDENTITY_SCALER = (baseValue, mobLevel, attributeName) -> baseValue;

    @Override
    public MobLevelData getMobLevelData(LivingEntity entity) {
        // 返回未设置的默认实例（level=0），调用方按"isSet()=false"处理
        return new MobLevelData();
    }

    @Override
    public IMobAttributeScaler getScaler() {
        return IDENTITY_SCALER; // 恒等：不按等级缩放
    }

    @Override
    public Optional<MobAttributeConfig.MobAttributes> getConfig(Identifier typeId) {
        return Optional.empty();
    }

    @Override
    public MobAttributeConfig.SpawnDistribution getSpawnDistribution(Identifier typeId) {
        return null;
    }
}
