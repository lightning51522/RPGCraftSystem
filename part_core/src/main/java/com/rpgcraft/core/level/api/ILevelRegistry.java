package com.rpgcraft.core.level.api;

import com.google.gson.JsonObject;

/**
 * 等级经验表注册中心接口
 * <p>
 * 提供等级经验阈值的注册和查询能力。
 * 默认实现为 {@link com.rpgcraft.core.level.LevelConfig}。
 * <p>
 * 其他模组通过 {@link ILevelProvider#registerLevelData(ILevelRegistry)}
 * 获取注册中心实例并注册自定义经验表条目。
 */
public interface ILevelRegistry {

    /**
     * 编程式注册等级升级经验需求
     *
     * @param level        等级编号（≥ 1）
     * @param expRequired  从该等级升到下一级所需的增量经验（≥ 0）
     */
    void registerExpRequirement(int level, int expRequired);

    /**
     * 查询从指定等级升到下一级所需的经验
     *
     * @param level 当前等级（1-based）
     * @return 升级所需经验，达到最大等级或等级无效时返回 -1
     */
    int getExpForLevel(int level);

    /**
     * 获取最大等级
     *
     * @return 最大等级值
     */
    int getMaxLevel();

    /**
     * 从 JSON 配置加载经验表数据
     * <p>
     * 用于资源重载监听器调用，支持 {@code /reload} 热更新。
     *
     * @param json 等级经验表配置 JSON
     */
    void loadFromJson(JsonObject json);
}
