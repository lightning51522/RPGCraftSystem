package com.rpgcraft.client.ui;

import com.rpgcraft.client.ClientMod;
import com.rpgcraft.core.network.RequestCharacterScreenPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 角色界面打开器 —— 快捷键注册与触发
 * <p>
 * 管理角色界面的快捷键绑定（默认 R 键），在客户端 tick 中检测按键按下，
 * 发送请求包到服务端并打开 {@link RPGCharacterScreen}。
 * <p>
 * <h3>数据流</h3>
 * <pre>
 * 玩家按下 R 键
 *   → {@link #onClientTick(ClientTickEvent.Post)} 检测到按键
 *   → 发送 {@link RequestCharacterScreenPacket} 到服务端
 *   → 立即打开 {@link RPGCharacterScreen}（显示"加载中"）
 *   → 服务端创建快照 → 回传 SyncAttributeSnapshotPacket
 *   → 客户端缓存到 UISnapshotCache → 界面刷新显示数据
 * </pre>
 * <p>
 * 注册方式：
 * <ul>
 *   <li>快捷键注册：在 ClientMod 中通过 {@code modEventBus.addListener(CharacterScreenOpener::registerKeyMapping)}</li>
 *   <li>Tick 事件：在 ClientMod 中通过 {@code NeoForge.EVENT_BUS.addListener(CharacterScreenOpener::onClientTick)}</li>
 * </ul>
 * <p>
 * <b>注意</b>：NeoForge 26.1.2 的 {@link KeyMapping} 构造函数使用
 * {@link KeyMapping.Category} 记录替代字符串分类，
 * 分类通过 {@link Identifier} 标识。
 *
 * @see RPGCharacterScreen
 * @see RequestCharacterScreenPacket
 */
public final class CharacterScreenOpener {

    /**
     * 角色界面快捷键分类
     * <p>
     * 使用 {@link KeyMapping.Category#register(Identifier)} 注册自定义分类，
     * 在游戏设置 → 按键绑定 中显示为独立分组。
     */
    public static final KeyMapping.Category RPGCRAFT_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("rpgcraftcore", "rpgcraft")
    );

    /**
     * 角色界面快捷键绑定
     * <p>
     * 默认绑定 R 键（{@link GLFW#GLFW_KEY_R}），
     * 仅在游戏内生效（非聊天/非其他界面打开时）。
     */
    public static final KeyMapping CHARACTER_SCREEN_KEY = new KeyMapping(
            "key.rpgcraft.character",
            GLFW.GLFW_KEY_R,
            RPGCRAFT_CATEGORY
    );

    private CharacterScreenOpener() {
        // 禁止实例化
    }

    /**
     * 快捷键注册回调（Mod 事件总线）
     * <p>
     * 在 ClientMod 构造函数中通过 {@code modEventBus.addListener(CharacterScreenOpener::registerKeyMapping)} 挂载。
     *
     * @param event NeoForge 快捷键注册事件
     */
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(CHARACTER_SCREEN_KEY);
        ClientMod.LOGGER.debug("注册角色界面快捷键：R");
    }

    /**
     * 客户端 tick 回调（Game 事件总线）
     * <p>
     * 每帧检测角色界面快捷键是否按下。按下时：
     * <ol>
     *   <li>发送 {@link RequestCharacterScreenPacket} 请求服务端快照</li>
     *   <li>立即打开 {@link RPGCharacterScreen}（先显示"加载中"，快照到达后自动刷新）</li>
     * </ol>
     * <p>
     * 仅在玩家存在且没有其他界面打开时响应按键。
     * 界面打开后按 R 键关闭由 {@link RPGCharacterScreen#keyPressed} 处理。
     *
     * @param event 客户端 tick 事件（Post 阶段）
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // 仅在游戏内（非暂停/无其他界面）且玩家存在时响应
        if (mc.player == null || mc.screen != null) return;

        if (CHARACTER_SCREEN_KEY.consumeClick()) {
            // 发送请求包到服务端
            mc.getConnection().send(new RequestCharacterScreenPacket());
            // 立即打开角色界面（快照到达前显示"加载中"）
            mc.setScreen(new RPGCharacterScreen());
        }
    }
}
