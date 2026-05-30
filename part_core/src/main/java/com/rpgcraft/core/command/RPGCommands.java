package com.rpgcraft.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.GenericEntityData;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * RPG 属性指令系统
 * <p>
 * 通过游戏内聊天指令查看和修改玩家的 RPG 属性值。
 * 使用 Brigadier 命令框架注册到 NeoForge 的 Game 事件总线。
 * <p>
 * <b>指令结构：</b>
 * <pre>
 * /rpg list [player]                        — 列出所有属性值
 * /rpg get &lt;attribute&gt; [player]            — 查询单个属性值
 * /rpg set &lt;attribute&gt; &lt;value&gt; [player]   — 设置属性值（需要 OP 权限）
 * </pre>
 * <p>
 * <b>参数说明：</b>
 * <ul>
 *   <li>{@code attribute} —— 属性英文名（life, strength, critical_rate 等）</li>
 *   <li>{@code value} —— 要设置的整数值</li>
 *   <li>{@code player} —— 可选目标玩家，不填则操作自身</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class RPGCommands {

    /**
     * 命令注册回调（Game 事件总线）
     * <p>
     * {@link RegisterCommandsEvent} 不实现 {@code IModBusEvent}，
     * 因此会自动路由到 Game 事件总线。
     *
     * @param event 命令注册事件
     */
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // /rpg list [player]
                .then(Commands.literal("list")
                        .executes(context -> executeList(context, context.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(context -> executeList(context, EntityArgument.getPlayer(context, "player")))
                        )
                )

                // /rpg get <attribute> [player]
                .then(Commands.literal("get")
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    // 提供属性名自动推测
                                    for (GenericEntityData.AttributeEntry entry : GenericEntityData.ALL_ATTRIBUTES) {
                                        builder.suggest(entry.id().getPath());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> executeGet(context,
                                        context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "attribute")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(context -> executeGet(context,
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "attribute")))
                                )
                        )
                )

                // /rpg set <attribute> <value> [player]
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("attribute", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (GenericEntityData.AttributeEntry entry : GenericEntityData.ALL_ATTRIBUTES) {
                                        builder.suggest(entry.id().getPath());
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(context -> executeSet(context,
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "attribute"),
                                                IntegerArgumentType.getInteger(context, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeSet(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "attribute"),
                                                        IntegerArgumentType.getInteger(context, "value")))
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 执行 /rpg list 命令
     * <p>
     * 遍历目标玩家的所有属性，以列表形式发送到聊天栏。
     */
    private static int executeList(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        for (GenericEntityData.AttributeEntry entry : GenericEntityData.ALL_ATTRIBUTES) {
            EntityAttribute attr = target.getData(entry.supplier());
            String path = entry.id().getPath();

            String text;
            if (attr.getMaxValue() == Integer.MAX_VALUE) {
                text = String.format("  %s: %d", path, attr.getValue());
            } else {
                text = String.format("  %s: %d / %d", path, attr.getValue(), attr.getMaxValue());
            }

            context.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        context.getSource().sendSuccess(
                () -> Component.literal("—— " + target.getName().getString() + " 的属性列表 ——"),
                false
        );
        return GenericEntityData.ALL_ATTRIBUTES.size();
    }

    /**
     * 执行 /rpg get 命令
     * <p>
     * 查询指定属性的当前值和最大值。
     */
    private static int executeGet(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName) {
        Optional<GenericEntityData.AttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知属性: " + attrName));
            return 0;
        }

        GenericEntityData.AttributeEntry entry = resolved.get();
        EntityAttribute attr = target.getData(entry.supplier());

        String text;
        if (attr.getMaxValue() == Integer.MAX_VALUE) {
            text = String.format("%s 的 %s: %d", target.getName().getString(), attrName, attr.getValue());
        } else {
            text = String.format("%s 的 %s: %d / %d", target.getName().getString(), attrName, attr.getValue(), attr.getMaxValue());
        }

        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return attr.getValue();
    }

    /**
     * 执行 /rpg set 命令
     * <p>
     * 设置指定属性的值。对于有上限属性（如 life），会同时更新 maxValue。
     * 设置后立即同步到客户端以确保 HUD 更新，若修改了 life 还会同步原版生命值。
     */
    private static int executeSet(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName, int value) {
        Optional<GenericEntityData.AttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.literal("未知属性: " + attrName));
            return 0;
        }

        GenericEntityData.AttributeEntry entry = resolved.get();
        EntityAttribute attr = target.getData(entry.supplier());

        // 对有上限属性，同步更新 maxValue（设置为满血状态）
        if (attr.getMaxValue() != Integer.MAX_VALUE) {
            attr.setMaxValue(value);
        }
        attr.setValue(value);

        // 若修改了 life，同步原版生命值
        if (entry.id().equals(GenericEntityData.LIFE_ID)) {
            var maxHealthAttr = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(attr.getMaxValue());
            }
            target.setHealth(attr.getValue());
        }

        // 同步到客户端，使 HUD 立即更新
        SyncPlayerAttributePacket.sendToClient(target, entry.id(), attr);

        String text = String.format("已将 %s 的 %s 设置为 %d", target.getName().getString(), attrName, attr.getValue());
        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return attr.getValue();
    }

    /**
     * 将属性名字符串解析为 AttributeEntry
     * <p>
     * 通过遍历 {@link GenericEntityData#ALL_ATTRIBUTES}，
     * 匹配 Identifier 的 path 部分与输入字符串。
     *
     * @param name 属性英文名（如 "life", "strength"）
     * @return 匹配的 AttributeEntry，未找到则返回 empty
     */
    private static Optional<GenericEntityData.AttributeEntry> resolveAttribute(String name) {
        for (GenericEntityData.AttributeEntry entry : GenericEntityData.ALL_ATTRIBUTES) {
            if (entry.id().getPath().equals(name)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
