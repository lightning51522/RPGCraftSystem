package com.rpgcraft.core.level.api;

/**
 * 等级系统 SPI 接口
 * <p>
 * 其他模组实现此接口来注册自定义等级经验表条目（如添加更高等级段）。
 * 通过 Java {@link java.util.ServiceLoader} 发现。
 * <p>
 * 示例：
 * <pre>
 * public class MyModLevel implements ILevelProvider {
 *     public void registerLevelData(ILevelRegistry registry) {
 *         registry.registerExpRequirement(100, 50000);
 *         registry.registerExpRequirement(101, 60000);
 *     }
 * }
 * </pre>
 */
public interface ILevelProvider {

    /**
     * 注册自定义等级经验数据到注册中心
     *
     * @param registry 等级注册中心实例
     */
    void registerLevelData(ILevelRegistry registry);
}
