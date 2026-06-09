package com.rpgcraft.core.ui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * UI 插件静态注册表
 * <p>
 * 各模块在初始化时调用 {@link #register(ICharacterScreenPlugin)} 注册界面插件，
 * {@code RPGCharacterScreen} 通过 {@link #getPlugins()} 获取所有已注册插件并按顺序流式布局。
 * <p>
 * 使用 {@link CopyOnWriteArrayList} 保证线程安全：
 * 插件注册在模组初始化阶段（主线程），读取在渲染帧中（渲染线程），
 * 两者可能并发访问。
 * <p>
 * 使用模式：
 * <pre>
 * // client 模块初始化时注册
 * RPGUIPlugins.register(new AttributeListPlugin());
 * RPGUIPlugins.register(new PlayerInfoPlugin());
 *
 * // RPGCharacterScreen 渲染时读取
 * for (ICharacterScreenPlugin plugin : RPGUIPlugins.getPlugins()) {
 *     plugin.render(graphics, x, currentY, width, snapshot);
 *     currentY += plugin.getHeight();
 * }
 * </pre>
 *
 * @see ICharacterScreenPlugin
 */
public final class RPGUIPlugins {

    private static final Logger LOGGER = LoggerFactory.getLogger(RPGUIPlugins.class);

    /** 已注册的插件列表（线程安全） */
    private static final List<ICharacterScreenPlugin> plugins = new CopyOnWriteArrayList<>();

    private RPGUIPlugins() {
        // 禁止实例化
    }

    /**
     * 注册一个角色界面插件
     * <p>
     * 插件按注册顺序排列，先注册的插件显示在界面上方。
     * 注册操作通常在模组 {@code @Mod} 构造函数中完成。
     *
     * @param plugin 插件实例
     * @throws IllegalArgumentException 如果 plugin 为 null
     */
    public static void register(ICharacterScreenPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("插件不能为 null");
        }
        plugins.add(plugin);
        LOGGER.debug("注册角色界面插件：{}", plugin.getClass().getSimpleName());
    }

    /**
     * 获取所有已注册插件（只读视图）
     * <p>
     * 返回的列表按注册顺序排列，且不可修改。
     *
     * @return 不可修改的插件列表
     */
    public static List<ICharacterScreenPlugin> getPlugins() {
        return Collections.unmodifiableList(plugins);
    }
}
