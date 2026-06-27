package com.rpgcraft.attributepoints;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
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
 * 属性点模块命令
 * <p>
 * 提供属性点的查看、授予和重置命令接口。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg attrpoints [player]            — 查看可分配点数与各属性已分配情况
 * /rpg attrpoints add <count> [player] — 授予可分配点数（GM 权限）
 * /rpg attrpoints reset [player]       — 重置全部分配，退还所有已分配点数（GM 权限）
 * </pre>
 */
@EventBusSubscriber(modid = AttributePointsMod.MODID)
public class AttributePointsCommands {

    private AttributePointsCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")
                .then(Commands.literal("attrpoints")
                        .executes(context -> executeInfo(context,
                                context.getSource().getPlayerOrException()))
                        .then(Commands.literal("add")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                        .executes(context -> executeAdd(context,
                                                context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeAdd(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "count")))
                                        )
                                )
                        )
                        .then(Commands.literal("reset")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> executeReset(context,
                                        context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> executeReset(context,
                                                EntityArgument.getPlayer(context, "player")))
                                )
                        )
                )
        );
    }

    /**
     * 显示玩家的属性点信息
     */
    private static int executeInfo(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (!RPGSystems.hasAttributePointSystem()) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.attributepoints.not_loaded"));
            return 0;
        }

        int available = RPGSystems.getAttributePointSystem().getAvailablePoints(target);
        Map<Identifier, Integer> allocations = RPGSystems.getAttributePointSystem().getAllAllocations(target);

        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.attributepoints.info_header", target.getName()),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.attributepoints.available", available),
                false
        );

        if (allocations.isEmpty()) {
            context.getSource().sendSuccess(
                    () -> Component.translatable("rpgcraft.attributepoints.allocated_none"),
                    false
            );
        } else {
            context.getSource().sendSuccess(
                    () -> Component.translatable("rpgcraft.attributepoints.allocated_header"),
                    false
            );
            int totalAllocated = 0;
            for (Map.Entry<Identifier, Integer> entry : allocations.entrySet()) {
                totalAllocated += entry.getValue();
                String attrName = entry.getKey().getPath();
                int value = entry.getValue();
                context.getSource().sendSuccess(
                        () -> Component.translatable("rpgcraft.attributepoints.allocated_entry", attrName, value),
                        false
                );
            }
            int finalTotal = totalAllocated;
            context.getSource().sendSuccess(
                    () -> Component.translatable("rpgcraft.attributepoints.total", finalTotal),
                    false
            );
        }
        return 1;
    }

    /**
     * 授予玩家可分配点数
     */
    private static int executeAdd(CommandContext<CommandSourceStack> context, ServerPlayer target, int count) {
        RPGSystems.getAttributePointSystem().grantPoints(target, count);
        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.attributepoints.granted", target.getName(), count),
                true
        );
        return count;
    }

    /**
     * 重置玩家的全部分配
     */
    private static int executeReset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        RPGSystems.getAttributePointSystem().reset(target);
        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.attributepoints.reset", target.getName()),
                true
        );
        return 1;
    }
}
