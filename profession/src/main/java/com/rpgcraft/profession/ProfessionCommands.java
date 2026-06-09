package com.rpgcraft.profession;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.registry.RPGSystems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Map;

/**
 * 职业模块命令
 * <p>
 * 提供职业查看、列表和设置的命令接口。
 * 这些命令原本位于 core 的 {@code RPGCommands} 中，
 * Phase 10 重构后迁移到职业模块中，实现命令与模块的归属一致性。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg profession [player]           — 查看当前职业信息
 * /rpg profession list               — 列出所有可用职业
 * /rpg profession set <name> [player] — 设置职业
 * </pre>
 */
@EventBusSubscriber(modid = ProfessionMod.MODID)
public class ProfessionCommands {

    private ProfessionCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 职业系统命令 ===
                .then(Commands.literal("profession")
                        .executes(context -> executeProfessionInfo(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.literal("list")
                                .executes(context -> executeProfessionList(context))
                        )
                        .then(Commands.literal("set")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("profession", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (IProfession prof : RPGSystems.getProfessionSystem().getAllProfessions()) {
                                                builder.suggest(prof.getId().getPath());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeProfessionSet(context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "profession")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeProfessionSet(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "profession")))
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 显示玩家当前职业信息
     */
    private static int executeProfessionInfo(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        IProfession prof = RPGSystems.getProfessionSystem().getProfession(target);
        context.getSource().sendSuccess(
                () -> Component.literal("—— " + target.getName().getString() + " 的职业信息 ——"),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.literal("  职业: " + prof.getDisplayName()),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.literal("  描述: " + prof.getDescription()),
                false
        );

        if (!prof.getBonusMap().isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("  属性加成:"),
                    false
            );
            for (Map.Entry<Identifier, Integer> entry : prof.getBonusMap().entrySet()) {
                String attrName = entry.getKey().getPath();
                int bonus = entry.getValue();
                String sign = bonus >= 0 ? "+" : "";
                context.getSource().sendSuccess(
                        () -> Component.literal("    " + attrName + ": " + sign + bonus),
                        false
                );
            }
        } else {
            context.getSource().sendSuccess(
                    () -> Component.literal("  属性加成: 无"),
                    false
            );
        }
        return 1;
    }

    /**
     * 列出所有可用职业
     */
    private static int executeProfessionList(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal("—— 可用职业列表 ——"),
                false
        );
        for (IProfession prof : RPGSystems.getProfessionSystem().getAllProfessions()) {
            StringBuilder bonuses = new StringBuilder();
            if (!prof.getBonusMap().isEmpty()) {
                for (Map.Entry<Identifier, Integer> entry : prof.getBonusMap().entrySet()) {
                    String attrName = entry.getKey().getPath();
                    int bonus = entry.getValue();
                    String sign = bonus >= 0 ? "+" : "";
                    if (bonuses.length() > 0) bonuses.append(", ");
                    bonuses.append(attrName).append(sign).append(bonus);
                }
            }
            String bonusText = bonuses.length() > 0 ? " (" + bonuses + ")" : "";
            context.getSource().sendSuccess(
                    () -> Component.literal("  " + prof.getId().getPath() + " - " +
                            prof.getDisplayName() + ": " + prof.getDescription() + bonusText),
                    false
            );
        }
        return RPGSystems.getProfessionSystem().getAllProfessions().size();
    }

    /**
     * 设置玩家职业
     */
    private static int executeProfessionSet(CommandContext<CommandSourceStack> context,
                                            ServerPlayer target, String professionName) {
        // 通过路径查找职业
        IProfession resolved = RPGSystems.getProfessionSystem().getProfessionById(
                Identifier.fromNamespaceAndPath("rpgcraftcore", professionName));

        if (resolved == null) {
            context.getSource().sendFailure(Component.literal("未知职业: " + professionName));
            return 0;
        }

        IProfession current = RPGSystems.getProfessionSystem().getProfession(target);
        if (current.getId().equals(resolved.getId())) {
            context.getSource().sendFailure(
                    Component.literal(target.getName().getString() + " 已经是 " + resolved.getDisplayName()));
            return 0;
        }

        final IProfession targetProf = resolved;
        RPGSystems.getProfessionSystem().setProfession(target, targetProf.getId());

        context.getSource().sendSuccess(
                () -> Component.literal("已将 " + target.getName().getString() +
                        " 的职业设置为 " + targetProf.getDisplayName()),
                true
        );
        return 1;
    }
}
