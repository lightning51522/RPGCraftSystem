package com.rpgcraft.core.registry;

import com.rpgcraft.core.attribute.MobAttributeConfig;
import com.rpgcraft.core.combat.MobLevelData;
import com.rpgcraft.core.level.api.IMobAttributeScaler;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

/**
 * 怪物数据提供者接口
 * <p>
 * 提供怪物等级数据、属性配置、缩放器等查询能力，
 * 由等级模块注册实现，供战斗模块查询。
 * <p>
 * 解耦 combat → leveling 的直接依赖：
 * 战斗模块不再直接引用等级模块的 {@code LevelManager} 的
 * {@code MOB_LEVEL} 附件和 {@link MobAttributeConfig} 静态方法，
 * 而是通过 {@link RPGSystems#getMobDataProvider()} 获取数据。
 *
 * @see RPGSystems#registerMobDataProvider(IMobDataProvider)
 * @see RPGSystems#getMobDataProvider()
 */
public interface IMobDataProvider {

    /**
     * 获取实体的怪物等级数据
     *
     * @param entity 生物实体
     * @return 怪物等级数据附件
     */
    MobLevelData getMobLevelData(LivingEntity entity);

    /**
     * 获取怪物属性缩放器
     *
     * @return 缩放器实例
     */
    IMobAttributeScaler getScaler();

    /**
     * 查询指定生物类型的属性配置
     *
     * @param typeId 生物类型标识符（如 minecraft:zombie）
     * @return 属性配置，未配置则返回 empty
     */
    Optional<MobAttributeConfig.MobAttributes> getConfig(Identifier typeId);

    /**
     * 查询指定生物类型的随机刷新分布配置
     *
     * @param typeId 生物类型标识符
     * @return 权重分布，未配置则返回 null
     */
    MobAttributeConfig.SpawnDistribution getSpawnDistribution(Identifier typeId);
}
