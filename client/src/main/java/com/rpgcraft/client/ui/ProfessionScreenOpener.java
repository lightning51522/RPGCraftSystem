package com.rpgcraft.client.ui;

import com.rpgcraft.client.ClientMod;
import com.rpgcraft.core.network.RequestProfessionStatePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 职业面板打开器 —— 快捷键注册与触发
 * <p>
 * 管理职业面板的快捷键绑定（默认 P 键），复用 {@link CharacterScreenOpener#RPGCRAFT_CATEGORY}
 * 分类。在客户端 tick 中检测按键按下，发送请求包到服务端并打开 {@link RPGProfessionScreen}。
 * <p>
 * 数据流与 {@link CharacterScreenOpener} 一致：发请求包 → 立即打开（显示加载中）→
 * 服务端回传 {@code SyncProfessionStatePacket} → 客户端缓存到
 * {@link com.rpgcraft.core.ui.ProfessionStateCache} → 面板刷新。
 *
 * @see RPGProfessionScreen
 * @see RequestProfessionStatePacket
 */
public final class ProfessionScreenOpener {

    /** 职业面板快捷键（默认 P 键） */
    public static final KeyMapping PROFESSION_SCREEN_KEY = new KeyMapping(
            "key.rpgcraft.profession",
            GLFW.GLFW_KEY_P,
            CharacterScreenOpener.RPGCRAFT_CATEGORY
    );

    private ProfessionScreenOpener() {
    }

    /**
     * 快捷键注册回调（Mod 事件总线）
     * <p>
     * 分类 {@link CharacterScreenOpener#RPGCRAFT_CATEGORY} 由 {@link CharacterScreenOpener}
     * 先注册，本类只注册键本身。
     */
    public static void registerKeyMapping(RegisterKeyMappingsEvent event) {
        event.register(PROFESSION_SCREEN_KEY);
        ClientMod.LOGGER.debug("注册职业面板快捷键：P");
    }

    /**
     * 客户端 tick 回调（Game 事件总线）
     * <p>
     * 仅在玩家存在且无其他界面打开时响应。
     */
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        if (PROFESSION_SCREEN_KEY.consumeClick()) {
            mc.getConnection().send(new RequestProfessionStatePacket());
            mc.setScreen(new RPGProfessionScreen());
        }
    }
}
