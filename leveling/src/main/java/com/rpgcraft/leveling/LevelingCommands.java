package com.rpgcraft.leveling;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.attribute.AttributeSnapshotManager;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 等级模块命令
 * <p>
 * 提供等级查看、设置和经验增加的命令接口。
 * 这些命令原本位于 core 的 {@code RPGCommands} 中，
 * Phase 10 重构后迁移到等级模块中，实现命令与模块的归属一致性。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg level [player]                  — 查看等级
 * /rpg setlevel <level> [player]       — 设置等级
 * /rpg addexp <amount> [player]        — 增加经验
 * </pre>
 */
@EventBusSubscriber(modid = LevelingMod.MODID)
public class LevelingCommands {

    private LevelingCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 等级系统命令 ===
                .then(Commands.literal("level")
                        .executes(context -> executeLevel(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> executeLevel(context,
                                        EntityArgument.getPlayer(context, "player")))
                        )
                )

                .then(Commands.literal("setlevel")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                .executes(context -> executeSetLevel(context,
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "level")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> executeSetLevel(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "level")))
                                )
                        )
                )

                .then(Commands.literal("addexp")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> executeAddExp(context,
                                        context.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(context, "amount")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> executeAddExp(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "amount")))
                                )
                        )
                )
        );
    }

    /**
     * 查看玩家等级和经验信息
     */
    private static int executeLevel(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        var levelSystem = RPGSystems.getLevelSystem();
        int level = levelSystem.getLevel(target);
        int expForNext = levelSystem.getExpForNextLevel(target);

        String text;
        if (expForNext < 0) {
            text = String.format("%s 的等级: %d (MAX)", target.getName().getString(), level);
        } else {
            text = String.format("%s 的等级: %d  经验: %d / %d",
                    target.getName().getString(), level, levelSystem.getExperience(target), expForNext);
        }

        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return level;
    }

    /**
     * 设置玩家等级
     */
    private static int executeSetLevel(CommandContext<CommandSourceStack> context, ServerPlayer target, int level) {
        var levelSystem = RPGSystems.getLevelSystem();
        levelSystem.setLevel(target, level);
        levelSystem.setExperience(target, 0);
        levelSystem.syncToClient(target);

        // 标记属性快照脏（等级变更可能影响属性计算）
        AttributeSnapshotManager.markDirty(target);

        String text = String.format("已将 %s 的等级设置为 %d", target.getName().getString(), level);
        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return level;
    }

    /**
     * 为玩家增加经验
     */
    private static int executeAddExp(CommandContext<CommandSourceStack> context, ServerPlayer target, int amount) {
        var levelSystem = RPGSystems.getLevelSystem();
        int oldLevel = levelSystem.getLevel(target);
        levelSystem.addExperience(target, amount);
        levelSystem.syncToClient(target);

        int newLevel = levelSystem.getLevel(target);

        // 升级时标记属性快照脏（为未来属性点/升级加成做准备）
        if (newLevel > oldLevel) {
            AttributeSnapshotManager.markDirty(target);
        }
        String text;
        if (newLevel > oldLevel) {
            text = String.format("已为 %s 增加 %d 经验（%d → %d 级）",
                    target.getName().getString(), amount, oldLevel, newLevel);
        } else {
            text = String.format("已为 %s 增加 %d 经验（当前 %d / %d）",
                    target.getName().getString(), amount, levelSystem.getExperience(target), levelSystem.getExpForNextLevel(target));
        }

        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return amount;
    }
}
