package com.rpgcraft.core.ui;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 角色界面插件接口（仅客户端运行时调用）
 * <p>
 * 每个插件负责角色界面中的一个区域（如属性列表、等级信息、装备摘要等）。
 * 由 client 模块或其他插件模块注册实现，由 {@code RPGCharacterScreen}
 * 按注册顺序进行流式布局渲染。
 * <p>
 * 插件通过 {@link RPGUIPlugins#register(ICharacterScreenPlugin)} 注册，
 * 角色界面打开时从 {@link RPGUIPlugins#getPlugins()} 获取所有已注册插件。
 * <p>
 * 渲染数据通过 {@link AttributeSnapshot} 传入（数据-渲染分离），
 * 插件不应直接访问实体的 Attachment。
 * <p>
 * <b>注意</b>：此接口引用客户端类 {@link GuiGraphicsExtractor}，
 * 在 NeoForge moddev 环境下 core 编译期可引用，但仅限客户端运行时调用。
 *
 * @see RPGUIPlugins
 * @see AttributeSnapshot
 */
public interface ICharacterScreenPlugin {

    /**
     * 获取此插件占用的像素高度
     * <p>
     * 布局容器根据此值分配渲染区域。
     * 通常应保持稳定；确需随玩家状态变化时（如多个副职业的显示行数），
     * 同一帧内的查询必须返回一致值（布局容器在同一渲染/点击流程内多次调用）。
     *
     * @return 像素高度
     */
    int getHeight();

    /**
     * 在给定区域渲染插件内容
     *
     * @param graphics 图形上下文
     * @param x        渲染区域左上角 X 坐标
     * @param y        渲染区域左上角 Y 坐标
     * @param width    渲染区域宽度
     * @param snapshot 属性快照（所有属性数据的只读视图）
     */
    void render(GuiGraphicsExtractor graphics, int x, int y, int width, AttributeSnapshot snapshot);

    /**
     * 处理鼠标点击事件
     * <p>
     * 坐标为相对于此插件渲染区域的局部坐标。
     * 默认实现返回 {@code false}（不消费事件）。
     *
     * @param mouseX 鼠标 X（相对于渲染区域左上角）
     * @param mouseY 鼠标 Y（相对于渲染区域左上角）
     * @param button 鼠标按钮（0=左键，1=右键，2=中键）
     * @return {@code true} 如果消费了此事件，阻止后续插件处理
     */
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * 返回鼠标悬停在插件指定位置时应显示的 tooltip 文字行
     * <p>
     * 坐标为相对于此插件渲染区域的局部坐标（左上角为 0,0），与 {@link #mouseClicked} 一致。
     * 返回 {@code null} 或空列表表示该位置无 tooltip。
     * 默认实现返回 {@code null}（无 tooltip）。
     *
     * @param relX     鼠标 X（相对于渲染区域左上角）
     * @param relY     鼠标 Y（相对于渲染区域左上角）
     * @param width    渲染区域宽度
     * @param snapshot 属性快照（所有属性数据的只读视图）
     * @return tooltip 文字行列表，或 {@code null}
     */
    default List<Component> getTooltip(double relX, double relY, int width, AttributeSnapshot snapshot) {
        return null;
    }
}
