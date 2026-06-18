package com.rpgcraft.skills;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.skill.api.ISkill;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 技能模块命令
 * <p>
 * 提供技能查看、列表、强制释放、重置冷却的命令接口。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg skills                       — 查看已注册技能与当前冷却
 * /rpg skills list                  — 列出所有已注册技能定义
 * /rpg skills cast <skillId> [player] — 强制释放某技能（GM 调试，跳过资源/冷却校验前的查询）
 * /rpg skills cooldown reset [player] — 重置玩家全部技能冷却
 * </pre>
 */
@EventBusSubscriber(modid = SkillsMod.MODID)
public class SkillsCommands {

    private SkillsCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 技能系统命令 ===
                .then(Commands.literal("skills")
                        .executes(context -> executeSkillsInfo(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.literal("list")
                                .executes(SkillsCommands::executeSkillsList)
                        )
                        .then(Commands.literal("cast")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("skill", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (ISkill skill : RPGSystems.getSkillSystem().getAllSkills()) {
                                                builder.suggest(skill.getId().getPath());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeSkillsCast(context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "skill")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeSkillsCast(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "skill")))
                                        )
                                )
                        )
                        .then(Commands.literal("cooldown")
                                .then(Commands.literal("reset")
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(context -> executeCooldownReset(context,
                                                context.getSource().getPlayerOrException()))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeCooldownReset(context,
                                                        EntityArgument.getPlayer(context, "player")))
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 显示玩家技能信息（已注册技能 + 各自冷却状态）
     */
    private static int executeSkillsInfo(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        var sys = RPGSystems.getSkillSystem();
        context.getSource().sendSuccess(
                () -> Component.literal("—— " + target.getName().getString() + " 的技能信息 ——"),
                false
        );
        if (sys.getAllSkills().isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("  （无已注册技能）"), false
            );
        } else {
            for (ISkill skill : sys.getAllSkills()) {
                boolean onCd = sys.isOnCooldown(target, skill.getId());
                long remaining = sys.getRemainingCooldown(target, skill.getId());
                String status = onCd ? " [冷却 " + (remaining / 20.0) + "s]" : " [可用]";
                context.getSource().sendSuccess(
                        () -> Component.literal("  " + skill.getId().getPath() + " - " +
                                skill.getDisplayName() + status +
                                " (消耗 " + skill.getResourceCost() + " 技能点, " +
                                "冷却 " + (skill.getCooldownTicks() / 20.0) + "s, " +
                                "伤害 " + skill.getDamageAmount() + ")"),
                        false
                );
            }
        }
        return 1;
    }

    /**
     * 列出所有已注册技能定义
     */
    private static int executeSkillsList(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(
                () -> Component.literal("—— 已注册技能列表 ——"), false
        );
        for (ISkill skill : RPGSystems.getSkillSystem().getAllSkills()) {
            context.getSource().sendSuccess(
                    () -> Component.literal("  " + skill.getId().getPath() +
                            " - " + skill.getDisplayName() + ": " + skill.getDescription() +
                            " [消耗" + skill.getResourceCost() + "/冷却" + (skill.getCooldownTicks() / 20.0) +
                            "s/伤害" + skill.getDamageAmount() + "/范围" + skill.getRange() + "]"),
                    false
            );
        }
        return RPGSystems.getSkillSystem().getAllSkills().size();
    }

    /**
     * 强制释放某技能（GM 调试，仍走完整 cast 校验链）
     */
    private static int executeSkillsCast(CommandContext<CommandSourceStack> context,
                                         ServerPlayer target, String skillName) {
        Identifier skillId = Identifier.fromNamespaceAndPath(SkillsDefinitionLoader.NAMESPACE, skillName);
        var sys = RPGSystems.getSkillSystem();
        ISkill skill = sys.getSkillById(skillId);
        if (skill == null) {
            context.getSource().sendFailure(Component.literal("未知技能: " + skillName));
            return 0;
        }

        boolean success = sys.cast(target, skillId);
        if (success) {
            context.getSource().sendSuccess(
                    () -> Component.literal(target.getName().getString() +
                            " 释放了 " + skill.getDisplayName()), true
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal(
                    target.getName().getString() + " 无法释放 " + skill.getDisplayName() +
                            "（冷却中或技能点不足）"));
            return 0;
        }
    }

    /**
     * 重置玩家全部技能冷却
     */
    private static int executeCooldownReset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        RPGSystems.getSkillSystem().resetAllCooldowns(target);
        context.getSource().sendSuccess(
                () -> Component.literal("已重置 " + target.getName().getString() + " 的全部技能冷却"), true
        );
        return 1;
    }
}
