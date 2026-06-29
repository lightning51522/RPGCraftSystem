package com.rpgcraft.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.RPGCraftCore;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.snapshot.DeathRestoreMode;
import com.rpgcraft.core.snapshot.DeathRestoreModeSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * RPG 核心属性指令系统
 * <p>
 * 通过游戏内聊天指令查看和修改玩家的 RPG 属性值。
 * 这些命令完全<b>与具体属性解耦</b>：遍历注册表中所有已注册属性（无论由哪个附属模块提供），
 * 不硬编码任何属性名。生命（LIFE）由 core 提供，set/setmax/reset 时同步原版血条。
 * <p>
 * 等级、职业、战斗日志、HUD、生物召唤等命令已迁移到各自的插件模块中。
 * <p>
 * 核心命令列表：
 * <pre>
 * /rpg list [player]
 * /rpg get &lt;attribute&gt; [player]
 * /rpg set &lt;attribute&gt; &lt;value&gt; [player]
 * /rpg setmax &lt;attribute&gt; &lt;value&gt; [player]
 * /rpg reset [player]
 * /rpg deathmode &lt;mode&gt;
 * </pre>
 * <p>
 * 已迁移的命令：
 * <ul>
 *   <li>{@code /rpg level/setlevel/addexp} → leveling 模块 {@code LevelingCommands}</li>
 *   <li>{@code /rpg profession [list|set]} → profession 模块 {@code ProfessionCommands}</li>
 *   <li>{@code /rpg combatlog} → combat 子系统（位于 attributes 模块）{@code CombatCommands}</li>
 *   <li>{@code /rpg randspawn} → combat 子系统（位于 attributes 模块）{@code CombatCommands}</li>
 *   <li>{@code /rpg spawn} → combat 子系统（位于 attributes 模块）{@code CombatCommands}</li>
 *   <li>{@code /rpg hud} → client 模块 {@code ClientCommands}</li>
 * </ul>
 */
@EventBusSubscriber(modid = RPGCraftCore.MODID)
public class RPGCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 属性指令：/rpg attribute <list|get|set|setmax|reset> ... ===
                .then(Commands.literal("attribute")

                        .then(Commands.literal("list")
                                .executes(context -> executeList(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                        .executes(context -> executeList(context, EntityArgument.getPlayer(context, "player")))
                                )
                        )

                        .then(Commands.literal("get")
                                .then(Commands.argument("attribute", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
                                                builder.suggest(entry.getId().getPath());
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

                        .then(Commands.literal("set")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("attribute", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
                                                builder.suggest(entry.getId().getPath());
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

                        .then(Commands.literal("setmax")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("attribute", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
                                                builder.suggest(entry.getId().getPath());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(context -> executeSetMax(context,
                                                        context.getSource().getPlayerOrException(),
                                                        StringArgumentType.getString(context, "attribute"),
                                                        IntegerArgumentType.getInteger(context, "value")))
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> executeSetMax(context,
                                                                EntityArgument.getPlayer(context, "player"),
                                                                StringArgumentType.getString(context, "attribute"),
                                                                IntegerArgumentType.getInteger(context, "value")))
                                                )
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

                // === core 全局指令：/rpg core <deathmode ...> ===
                .then(Commands.literal("core")
                        .then(Commands.literal("deathmode")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (DeathRestoreMode mode : DeathRestoreMode.values()) {
                                                builder.suggest(mode.getCommandKey());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeDeathMode(context,
                                                StringArgumentType.getString(context, "mode")))
                                )
                        )
                )
        );
    }

    private static int executeList(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            IAttribute attr = target.getData(entry.getSupplier());
            String path = entry.getId().getPath();

            Component entryMsg;
            if (attr.hasMaxValue()) {
                entryMsg = Component.translatable("rpgcraft.attribute.list_entry_max", path, attr.getValue(), attr.getMaxValue());
            } else {
                entryMsg = Component.translatable("rpgcraft.attribute.list_entry", path, attr.getValue());
            }

            context.getSource().sendSuccess(() -> entryMsg, false);
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.attribute.list_header", target.getName()),
                false
        );
        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.core.deathmode.current", DeathRestoreMode.getCurrentMode().getDisplayName()),
                false
        );
        return AttributeManager.getRegistry().getAllEntries().size();
    }

    private static int executeGet(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName) {
        Optional<IAttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.attribute.unknown", attrName));
            return 0;
        }

        IAttributeEntry entryAttr = resolved.get();
        IAttribute attr = target.getData(entryAttr.getSupplier());

        Component msg = attr.hasMaxValue()
                ? Component.translatable("rpgcraft.attribute.get_max", target.getName(), attrName, attr.getValue(), attr.getMaxValue())
                : Component.translatable("rpgcraft.attribute.get", target.getName(), attrName, attr.getValue());

        context.getSource().sendSuccess(() -> msg, false);
        return attr.getValue();
    }

    private static int executeSet(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName, int value) {
        Optional<IAttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.attribute.unknown", attrName));
            return 0;
        }

        IAttributeEntry entry = resolved.get();
        IAttribute attr = target.getData(entry.getSupplier());

        attr.setValue(value);

        if (entry.getId().equals(AttributeManager.LIFE_ID)) {
            AttributeManager.syncVanillaHealth(target);
        }

        RPGCraftCore.checkAndSnapshotIfDying(target);

        EntityAttribute entityAttr = (EntityAttribute) attr;
        SyncPlayerAttributePacket.sendToClient(target, entry.getId(), entityAttr);

        context.getSource().sendSuccess(() -> Component.translatable("rpgcraft.attribute.set",
                target.getName(), attrName, attr.getValue()), true);
        return attr.getValue();
    }

    private static int executeSetMax(CommandContext<CommandSourceStack> context, ServerPlayer target, String attrName, int value) {
        Optional<IAttributeEntry> resolved = resolveAttribute(attrName);
        if (resolved.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.attribute.unknown", attrName));
            return 0;
        }

        IAttributeEntry entry = resolved.get();
        IAttribute attr = target.getData(entry.getSupplier());

        attr.setMaxValue(value);

        if (entry.getId().equals(AttributeManager.LIFE_ID)) {
            AttributeManager.syncVanillaHealth(target);
        }

        // setmax 可能导致 currentValue 被钳制到极低值，检测是否需要创建死亡快照
        RPGCraftCore.checkAndSnapshotIfDying(target);

        EntityAttribute entityAttr = (EntityAttribute) attr;
        SyncPlayerAttributePacket.sendToClient(target, entry.getId(), entityAttr);

        context.getSource().sendSuccess(() -> Component.translatable("rpgcraft.attribute.setmax",
                target.getName(), attrName, value), true);
        return value;
    }

    private static int executeDeathMode(CommandContext<CommandSourceStack> context, String modeKey) {
        DeathRestoreMode mode = DeathRestoreMode.fromCommandKey(modeKey);
        if (mode == null) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.core.deathmode.unknown", modeKey));
            return 0;
        }
        DeathRestoreModeSavedData.setCurrentMode(context.getSource().getServer(), mode);
        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.core.deathmode.set", mode.getDisplayName()),
                true
        );
        return 1;
    }

    private static Optional<IAttributeEntry> resolveAttribute(String name) {
        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            if (entry.getId().getPath().equals(name)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static int executeReset(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        AttributeManager.getRegistry().resetToDefaults(target);

        for (IAttributeEntry entry : AttributeManager.getRegistry().getAllEntries()) {
            EntityAttribute attr = (EntityAttribute) target.getData(entry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(target, entry.getId(), attr);
        }

        // 重置后同步原版生命值，确保原版血条与自定义 life 属性一致
        AttributeManager.syncVanillaHealth(target);

        context.getSource().sendSuccess(
                () -> Component.translatable("rpgcraft.attribute.reset", target.getName()),
                true
        );
        return AttributeManager.getRegistry().getAllEntries().size();
    }
}
