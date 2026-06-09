package com.rpgcraft.combat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 战斗模块命令
 * <p>
 * 提供战斗日志开关和随机刷新开关的命令接口。
 * 这些命令和对应的状态管理原本位于 core 的 {@code RPGCommands} 中，
 * Phase 10 重构后迁移到战斗模块中，实现命令与模块的归属一致性。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg combatlog          — 查看战斗日志开关状态
 * /rpg combatlog on/off   — 开关战斗日志
 * /rpg randspawn          — 查看随机刷新开关状态
 * /rpg randspawn on/off   — 开关随机刷新
 * </pre>
 */
@EventBusSubscriber(modid = CombatMod.MODID)
public class CombatCommands {

    private CombatCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 战斗日志开关指令 ===
                .then(Commands.literal("combatlog")
                        .executes(context -> executeCombatLogStatus(context))
                        .then(Commands.literal("on")
                                .executes(context -> executeCombatLogToggle(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> executeCombatLogToggle(context, false)))
                )

                // === 随机刷新开关指令 ===
                .then(Commands.literal("randspawn")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> executeRandSpawnStatus(context))
                        .then(Commands.literal("on")
                                .executes(context -> executeRandSpawnToggle(context, true)))
                        .then(Commands.literal("off")
                                .executes(context -> executeRandSpawnToggle(context, false)))
                )
        );
    }

    // === 战斗日志开关（每玩家，默认关闭） ===

    /** 每个玩家的战斗日志开关状态，默认关闭（调试功能） */
    private static final Map<UUID, Boolean> playerCombatLogEnabled = new ConcurrentHashMap<>();

    /**
     * 查询指定玩家的战斗日志是否启用
     *
     * @param playerId 玩家 UUID
     * @return true 表示启用战斗日志
     */
    public static boolean isCombatLogEnabled(UUID playerId) {
        return playerCombatLogEnabled.getOrDefault(playerId, false);
    }

    /**
     * 显示当前战斗日志开关状态
     */
    private static int executeCombatLogStatus(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean enabled = isCombatLogEnabled(player.getUUID());
        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("战斗日志状态: " + status),
                false
        );
        return enabled ? 1 : 0;
    }

    /**
     * 切换战斗日志开关状态
     */
    private static int executeCombatLogToggle(CommandContext<CommandSourceStack> context, boolean enabled)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        playerCombatLogEnabled.put(player.getUUID(), enabled);

        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("战斗日志已" + status),
                true
        );
        return enabled ? 1 : 0;
    }

    // === 随机刷新开关（全局，默认关闭） ===

    /** 全局随机刷新开关，默认关闭。开启后自然刷新的怪物会从权重表中随机选择等级和评级 */
    private static volatile boolean randomSpawnEnabled = false;

    /**
     * 查询随机刷新是否启用
     *
     * @return true = 自然刷新使用权重表随机等级/评级
     */
    public static boolean isRandomSpawnEnabled() {
        return randomSpawnEnabled;
    }

    /**
     * 显示当前随机刷新开关状态
     */
    private static int executeRandSpawnStatus(CommandContext<CommandSourceStack> context) {
        String status = randomSpawnEnabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("随机刷新状态: " + status + "（自然生成的怪物" +
                        (randomSpawnEnabled ? "会从权重表中随机选择等级和评级" : "使用配置静态等级") + "）"),
                false
        );
        return randomSpawnEnabled ? 1 : 0;
    }

    /**
     * 切换随机刷新开关
     */
    private static int executeRandSpawnToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
        randomSpawnEnabled = enabled;

        String status = enabled ? "§a开启" : "§c关闭";
        context.getSource().sendSuccess(
                () -> Component.literal("随机刷新已" + status + "（" +
                        (enabled ? "自然生成的怪物将从权重表中随机选择等级和评级" : "自然生成的怪物使用配置静态等级") + "）"),
                true
        );
        return enabled ? 1 : 0;
    }
}
