package com.rpgcraft.client.ui;

import com.rpgcraft.core.ui.IAttributeRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 默认文本属性渲染器（仅客户端）
 * <p>
 * 以 "名称: 值" 格式渲染属性值。对于有上限的属性（maxValue < {@link Integer#MAX_VALUE}），
 * 显示为 "名称: 当前值/最大值"；无上限的属性仅显示 "名称: 当前值"。
 * <p>
 * 此渲染器为单例模式，通过 {@link #INSTANCE} 访问，无需重复创建。
 * 属性注册时若未指定自定义渲染器工厂（{@link com.rpgcraft.core.ui.IAttributeRendererFactory}），
 * 角色界面将使用此渲染器作为默认实现。
 * <p>
 * 需要特殊着色的属性（如暴击率），应注册独立的自定义渲染器，而非修改此默认实现。
 *
 * @see com.rpgcraft.core.ui.IAttributeRenderer
 * @see com.rpgcraft.core.ui.IAttributeRendererFactory
 */
public class DefaultTextRenderer implements IAttributeRenderer {

    /** 单例实例 */
    public static final DefaultTextRenderer INSTANCE = new DefaultTextRenderer();

    /** 默认文本颜色（白色，ARGB 格式） */
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private DefaultTextRenderer() {
        // 单例，禁止外部实例化
    }

    /**
     * 渲染单个属性文本
     * <p>
     * 格式规则：
     * <ul>
     *   <li>有上限属性：{@code "名称: 当前值/最大值"}（如 "生命: 80/100"）</li>
     *   <li>无上限属性：{@code "名称: 当前值"}（如 "攻击力: 15"）</li>
     * </ul>
     *
     * @param graphics     图形上下文
     * @param x            渲染区域左上角 X 坐标
     * @param y            渲染区域左上角 Y 坐标
     * @param width        渲染区域宽度（由布局容器提供）
     * @param currentValue 属性当前值
     * @param maxValue     属性最大值
     * @param displayName  属性显示名称
     */
    @Override
    public void render(GuiGraphicsExtractor graphics, int x, int y, int width,
                       int currentValue, int maxValue, String displayName) {
        Minecraft mc = Minecraft.getInstance();

        // 构建显示文本：有上限显示 "名称: 当前/最大"，无上限显示 "名称: 当前"
        String text;
        if (maxValue < Integer.MAX_VALUE) {
            text = displayName + ": " + currentValue + "/" + maxValue;
        } else {
            text = displayName + ": " + currentValue;
        }

        graphics.text(mc.font, text, x, y, COLOR_TEXT, false);
    }
}
