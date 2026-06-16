package com.rpgcraft.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.preference.PlayerPreferences;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 客户端模块命令
 * <p>
 * 提供 HUD 开关命令和状态管理。HUD 状态存储在服务端的
 * {@link PlayerPreferences} 附件中（持久化保存），
 * 通过 {@link ToggleHudPacket} 同步到客户端。
 * <p>
 * 这些命令和状态原本位于 core 的 {@code RPGCommands} 中，
 * Phase 10 重构后迁移到客户端模块中，实现命令与模块的归属一致性。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg hud          — 查看 HUD 开关状态
 * /rpg hud on/off   — 开关 HUD（属性面板 + 准星提示）
 * </pre>
 */
@EventBusSubscriber(modid = ClientMod.MODID)
public class ClientCommands {

    private ClientCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === HUD 开关指令 ===
                .then(Commands.literal("hud")
                        .executes(context -> executeHudStatus(context))
                        .then(Commands.literal("on")
                                .executes(context -> executeHudToggle(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> executeHudToggle(context, false)))
                )
        );
    }

    // === HUD 开关状态（服务端附件存储，持久化保存） ===

    /**
     * 查询指定玩家的 HUD 是否启用
     * <p>
     * 从 {@link PlayerPreferences} 附件读取，默认启用。
     *
     * @param player 服务端玩家
     * @return true 表示 HUD 已启用
     */
    public static boolean isHudEnabled(ServerPlayer player) {
        return player.getData(AttributeManager.PLAYER_PREFERENCES).isHudEnabled();
    }

    /**
     * 显示当前 HUD 开关状态
     */
    private static int executeHudStatus(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = isHudEnabled(player);
        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("HUD 状态: " + status + "（属性面板 + 准星提示）"),
                false
        );
        return enabled ? 1 : 0;
    }

    /**
     * 切换 HUD 开关状态
     * <p>
     * 写入 {@link PlayerPreferences} 附件并同步到客户端。
     */
    private static int executeHudToggle(CommandContext<CommandSourceStack> context, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PlayerPreferences prefs = player.getData(AttributeManager.PLAYER_PREFERENCES);
        prefs.setHudEnabled(enabled);

        // 同步到客户端（通过 client 模块注册的 IClientSystem 接口发送网络包）
        RPGSystems.getClientSystem().sendHudToggle(player, enabled);

        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("HUD 已" + status + "（属性面板 + 准星提示）"),
                true
        );
        return enabled ? 1 : 0;
    }
}
