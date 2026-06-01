package com.rpgcraft.core.level.api;

/**
 * 怪物属性等级缩放策略接口
 * <p>
 * 定义怪物属性如何随等级增长的算法。默认实现为
 * {@link com.rpgcraft.core.level.DefaultMobAttributeScaler}（每级 +5%）。
 * <p>
 * 其他模组可以替换此实现来提供自定义的等级缩放公式，
 * 通过 {@link com.rpgcraft.core.level.LevelManager#setMobScaler(IMobAttributeScaler)} 注入。
 */
public interface IMobAttributeScaler {

    /**
     * 根据怪物等级缩放属性值
     *
     * @param baseValue     基础属性值（来自配置）
     * @param mobLevel      怪物等级（≥ 1）
     * @param attributeName 属性名称（如 "life", "strength"），用于差异化缩放
     * @return 缩放后的属性值
     */
    int scaleAttribute(int baseValue, int mobLevel, String attributeName);
}
