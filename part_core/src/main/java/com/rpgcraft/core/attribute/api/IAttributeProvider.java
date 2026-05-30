package com.rpgcraft.core.attribute.api;

/**
 * 属性提供者 SPI
 * <p>
 * 其他模组实现此接口来注册自定义属性。
 * 通过 NeoForge 事件机制发现并调用。
 * <p>
 * 示例：
 * <pre>
 * public class MyModAttributes implements IAttributeProvider {
 *     public void registerAttributes(IAttributeRegistry registry) {
 *         registry.register(
 *             Identifier.fromNamespaceAndPath("mymod", "fire_resistance"),
 *             "火焰抗性", 0, 100
 *         );
 *     }
 * }
 * </pre>
 */
public interface IAttributeProvider {

    /**
     * 注册自定义属性到注册中心
     *
     * @param registry 属性注册中心实例
     */
    void registerAttributes(IAttributeRegistry registry);
}
