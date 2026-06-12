package com.rpgcraft.core.attribute.api;

import com.rpgcraft.core.attribute.DefaultAttributeRegistry;

/**
 * 属性模块 SPI
 * <p>
 * 第三方模组实现此接口，通过 {@link com.rpgcraft.core.registry.RPGSystems#registerAttributeModule(IAttributeModule, int)}
 * 注册自定义属性模块。默认实现由 {@code rpgcraftattributes} 模块提供。
 * <p>
 * 使用优先级覆盖机制：{@link com.rpgcraft.core.registry.RPGSystems#DEFAULT_PRIORITY}（官方模块）和
 * {@link com.rpgcraft.core.registry.RPGSystems#OVERRIDE_PRIORITY}（第三方替代模块）。
 * <p>
 * 示例：
 * <pre>{@code
 * public class MyAttributeModule implements IAttributeModule {
 *     private static final Identifier MY_STAT_ID =
 *             Identifier.fromNamespaceAndPath("mymod", "my_stat");
 *
 *     @Override
 *     public void registerAttributes(DefaultAttributeRegistry registry) {
 *         registry.register(MY_STAT_ID, "我的属性", 10, Integer.MAX_VALUE);
 *         // 只注册自己需要的游戏属性；生命(LIFE)由 core 提供，无需在此注册
 *     }
 * }
 *
 * // 在 @Mod 构造函数中：
 * RPGSystems.registerAttributeModule(new MyAttributeModule(), RPGSystems.OVERRIDE_PRIORITY);
 * }</pre>
 */
public interface IAttributeModule {

    /**
     * 将属性注册到共享注册中心
     * <p>
     * 在 {@code RegisterEvent} 触发时由 core 调用，此时可以安全地向
     * {@link DefaultAttributeRegistry} 注册属性 AttachmentType。
     *
     * @param registry 共享属性注册中心实例
     */
    void registerAttributes(DefaultAttributeRegistry registry);
}
