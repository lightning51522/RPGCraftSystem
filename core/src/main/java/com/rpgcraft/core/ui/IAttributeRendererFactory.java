package com.rpgcraft.core.ui;

/**
 * 属性渲染器工厂（仅客户端运行时调用）
 * <p>
 * 用于在属性注册时绑定自定义渲染器。
 * 使用工厂接口而非直接实例，因为 {@link IAttributeRenderer} 依赖客户端类
 * （如 {@code Font}、{@code GuiGraphicsExtractor}），
 * 在服务端 common 源码中无法直接实例化渲染器。
 * <p>
 * 使用模式：
 * <pre>
 * // 注册带自定义渲染器的属性
 * registry.register(id, "暴击率", 5, 300, false, false,
 *     () -> new CriticalRateRenderer()); // IAttributeRendererFactory
 * </pre>
 * <p>
 * 若不提供工厂（{@code null}），角色界面使用默认文本渲染器。
 *
 * @see IAttributeRenderer
 * @see com.rpgcraft.core.attribute.api.IAttributeEntry#getRendererFactory()
 */
@FunctionalInterface
public interface IAttributeRendererFactory {

    /**
     * 创建属性渲染器实例
     *
     * @return 渲染器实例
     */
    IAttributeRenderer createRenderer();
}
