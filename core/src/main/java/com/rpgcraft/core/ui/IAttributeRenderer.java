package com.rpgcraft.core.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 属性渲染器接口（仅客户端运行时调用）
 * <p>
 * 定义单个属性的渲染方式。默认实现为文本渲染（"名称: 值/最大值"），
 * 特殊属性（如暴击率）可注册自定义渲染器实现特殊着色或图形化显示。
 * <p>
 * 渲染器由 {@link IAttributeRendererFactory} 创建，通过
 * {@link com.rpgcraft.core.attribute.api.IAttributeEntry#getRendererFactory()}
 * 绑定到属性条目上。
 * <p>
 * <b>注意</b>：此接口引用客户端类 {@link GuiGraphicsExtractor}，
 * 在 NeoForge moddev 环境下 core 编译期可引用，但仅限客户端运行时调用。
 *
 * @see IAttributeRendererFactory
 * @see com.rpgcraft.core.attribute.api.IAttributeEntry
 */
public interface IAttributeRenderer {

    /**
     * 渲染单个属性
     *
     * @param graphics     图形上下文
     * @param x            渲染区域左上角 X 坐标
     * @param y            渲染区域左上角 Y 坐标
     * @param width        渲染区域宽度（由布局容器提供）
     * @param currentValue 属性当前值
     * @param maxValue     属性最大值
     * @param displayName  属性显示名称
     */
    void render(GuiGraphicsExtractor graphics, int x, int y, int width,
                int currentValue, int maxValue, String displayName);
}
