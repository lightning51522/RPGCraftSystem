package com.rpgcraft.equipment;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.RPGComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 装备模块命令
 * <p>
 * 提供 GM 强制设置玩家背包中指定 ID 物品的稀有度/等级，以及发放带指定稀有度/等级的装备。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg setrarity &lt;物品ID&gt; &lt;稀有度&gt; [player] — 设置指定玩家背包中所有该 ID 物品的稀有度
 * /rpg setlevel &lt;物品ID&gt; &lt;等级&gt; [player] — 设置指定玩家背包中所有该 ID 物品的装备等级
 * /rpg give equipment &lt;物品ID&gt; [稀有度] [等级] [数量] [player] — 发放带指定稀有度/等级的装备
 * </pre>
 * <p>
 * 稀有度取 {@link EquipmentRarity} 的枚举名（小写，如 {@code white}/{@code blue}/{@code rainbow}），
 * 未匹配返回失败提示（不静默兜底为 GRAY）。装备等级为 0~6 的整数（0 = 清除等级）。
 * give 的稀有度/等级/数量/player 均可选，省略时分别默认 GRAY/0/1/自己。
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentCommands {

    /** 装备等级上限（与 EQUIPMENT_LEVEL 组件的 intRange 及 EquipmentLevelForgeHandler 一致）。 */
    private static final int MAX_LEVEL = 6;
    /** give 的默认发放数量。 */
    private static final int DEFAULT_GIVE_COUNT = 1;

    private EquipmentCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")

                // === 设置稀有度指令 ===
                .then(Commands.literal("setrarity")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("item", IdentifierArgument.id())
                                .suggests((context, builder) -> {
                                    BuiltInRegistries.ITEM.keySet().forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (EquipmentRarity r : EquipmentRarity.values()) {
                                                builder.suggest(r.name().toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeSetRarity(context,
                                                context.getSource().getPlayerOrException(),
                                                IdentifierArgument.getId(context, "item"),
                                                StringArgumentType.getString(context, "rarity")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeSetRarity(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IdentifierArgument.getId(context, "item"),
                                                        StringArgumentType.getString(context, "rarity")))
                                        )
                                )
                        )
                )

                // === 设置装备等级指令 ===
                .then(Commands.literal("setlevel")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("item", IdentifierArgument.id())
                                .suggests((context, builder) -> {
                                    BuiltInRegistries.ITEM.keySet().forEach(id -> builder.suggest(id.toString()));
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, MAX_LEVEL))
                                        .executes(context -> executeSetLevel(context,
                                                context.getSource().getPlayerOrException(),
                                                IdentifierArgument.getId(context, "item"),
                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> executeSetLevel(context,
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IdentifierArgument.getId(context, "item"),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level")))
                                        )
                                )
                        )
                )

                // === 发放装备指令（仿原版 give，附加稀有度/等级组件）===
                // /rpg give equipment <物品ID> [稀有度] [等级] [数量] [player]
                // 稀有度/等级/数量/player 均可选；省略时分别默认 GRAY/0/1/自己。
                // 位置参数链：物品 → 稀有度? → 等级? → 数量? → player?
                .then(Commands.literal("give")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("equipment")
                                .then(Commands.argument("item", IdentifierArgument.id())
                                        .suggests((context, builder) -> {
                                            BuiltInRegistries.ITEM.keySet().forEach(id -> builder.suggest(id.toString()));
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> executeGiveEquipment(context,
                                                context.getSource().getPlayerOrException(),
                                                IdentifierArgument.getId(context, "item"),
                                                null, 0, DEFAULT_GIVE_COUNT))
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (EquipmentRarity r : EquipmentRarity.values()) {
                                                        builder.suggest(r.name().toLowerCase());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> executeGiveEquipment(context,
                                                        context.getSource().getPlayerOrException(),
                                                        IdentifierArgument.getId(context, "item"),
                                                        StringArgumentType.getString(context, "rarity"),
                                                        0, DEFAULT_GIVE_COUNT))
                                                .then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, MAX_LEVEL))
                                                        .executes(context -> executeGiveEquipment(context,
                                                                context.getSource().getPlayerOrException(),
                                                                IdentifierArgument.getId(context, "item"),
                                                                StringArgumentType.getString(context, "rarity"),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"),
                                                                DEFAULT_GIVE_COUNT))
                                                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                                .executes(context -> executeGiveEquipment(context,
                                                                        context.getSource().getPlayerOrException(),
                                                                        IdentifierArgument.getId(context, "item"),
                                                                        StringArgumentType.getString(context, "rarity"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"),
                                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")))
                                                                .then(Commands.argument("player", EntityArgument.player())
                                                                        .executes(context -> executeGiveEquipment(context,
                                                                                EntityArgument.getPlayer(context, "player"),
                                                                                IdentifierArgument.getId(context, "item"),
                                                                                StringArgumentType.getString(context, "rarity"),
                                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "level"),
                                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count")))
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 设置指定玩家背包中所有匹配物品 ID 的堆叠的稀有度。
     * <p>
     * 遍历 {@link Inventory#getContainerSize()} 覆盖的全部槽位（主背包 + 护甲 + 副手），
     * 对每个非空且 ID 匹配的堆叠写入稀有度组件（GRAY 用 {@code remove} 省 NBT，与 roller 一致）。
     * 服务端写入后由 {@code networkSynchronized} 自动同步客户端，无需手动发包。
     *
     * @param context 命令上下文
     * @param target  目标玩家
     * @param itemId  物品 ID（如 minecraft:diamond_sword）
     * @param rarityName 稀有度枚举名（小写）
     * @return 修改的物品堆叠数（0 表示无匹配）
     */
    private static int executeSetRarity(CommandContext<CommandSourceStack> context, ServerPlayer target,
                                        Identifier itemId, String rarityName) {
        // 显式校验稀有度名（不依赖 fromName 的静默兜底，避免把 typo 误当 GRAY）
        EquipmentRarity rarity = matchRarity(rarityName);
        if (rarity == null) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.equipment.setrarity.unknown_rarity", rarityName));
            return 0;
        }

        Inventory inv = target.getInventory();
        int modified = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
            if (rarity == EquipmentRarity.GRAY) {
                stack.remove(RPGComponents.EQUIPMENT_RARITY.get()); // GRAY = 默认（无组件），remove 省 NBT
            } else {
                stack.set(RPGComponents.EQUIPMENT_RARITY.get(), rarity);
            }
            modified++;
        }

        if (modified == 0) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.equipment.setrarity.no_match",
                    target.getName(), itemId.toString()));
        } else {
            final int count = modified;
            context.getSource().sendSuccess(() -> Component.translatable("rpgcraft.equipment.setrarity",
                    count, target.getName(), rarity.name()), true);
        }
        return modified;
    }

    /**
     * 设置指定玩家背包中所有匹配物品 ID 的堆叠的装备等级。
     * <p>
     * 遍历 {@link Inventory#getContainerSize()} 覆盖的全部槽位（主背包 + 护甲 + 副手），
     * 对每个非空且 ID 匹配的堆叠写入等级组件（0 用 {@code remove} 省 NBT，与无等级物品一致）。
     * 服务端写入后由 {@code networkSynchronized} 自动同步客户端，无需手动发包。
     *
     * @param context 命令上下文
     * @param target  目标玩家
     * @param itemId  物品 ID（如 minecraft:diamond_sword）
     * @param level   目标等级（0~6，0 = 清除等级）
     * @return 修改的物品堆叠数（0 表示无匹配）
     */
    private static int executeSetLevel(CommandContext<CommandSourceStack> context, ServerPlayer target,
                                       Identifier itemId, int level) {
        Inventory inv = target.getInventory();
        int modified = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!itemId.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
            if (level <= 0) {
                stack.remove(RPGComponents.EQUIPMENT_LEVEL.get()); // 0 = 默认（无组件），remove 省 NBT
            } else {
                stack.set(RPGComponents.EQUIPMENT_LEVEL.get(), level);
            }
            modified++;
        }

        if (modified == 0) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.equipment.setlevel.no_match",
                    target.getName(), itemId.toString()));
        } else {
            final int count = modified;
            context.getSource().sendSuccess(() -> Component.translatable("rpgcraft.equipment.setlevel",
                    count, target.getName(), level), true);
        }
        return modified;
    }

    /**
     * 发放带指定稀有度/等级的装备到目标玩家背包（仿原版 give，附加 RPG 组件）。
     * <p>
     * 构造 {@code count} 个 {@code itemId} 物品堆叠，按稀有度/等级写入组件（GRAY/0 不写组件，与默认一致），
     * 经 {@code placeItemBackInInventory} 放入背包。服务端写组件后由 {@code networkSynchronized} 自动同步客户端。
     *
     * @param context    命令上下文
     * @param target     目标玩家
     * @param itemId     物品 ID（如 minecraft:diamond_sword）
     * @param rarityName 稀有度枚举名（小写）；{@code null} 表示省略 → 默认 GRAY（不写组件）
     * @param level      装备等级（0~6，0 = 不写组件）
     * @param count      发放数量（≥1）
     * @return 发放数量（成功）；0 表示物品 ID 未知或稀有度非法
     */
    private static int executeGiveEquipment(CommandContext<CommandSourceStack> context, ServerPlayer target,
                                            Identifier itemId, String rarityName, int level, int count) {
        // 校验物品 ID 是否注册
        var itemOpt = BuiltInRegistries.ITEM.getOptional(itemId);
        if (itemOpt.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("rpgcraft.equipment.give.unknown_item", itemId.toString()));
            return 0;
        }
        // 校验稀有度名（省略时 null → 默认 GRAY；提供时必须合法）
        EquipmentRarity rarity;
        if (rarityName == null) {
            rarity = EquipmentRarity.GRAY;
        } else {
            rarity = matchRarity(rarityName);
            if (rarity == null) {
                context.getSource().sendFailure(Component.translatable("rpgcraft.equipment.setrarity.unknown_rarity", rarityName));
                return 0;
            }
        }

        ItemStack stack = new ItemStack(itemOpt.get(), count);
        if (rarity != EquipmentRarity.GRAY) {
            stack.set(RPGComponents.EQUIPMENT_RARITY.get(), rarity);
        }
        if (level > 0) {
            stack.set(RPGComponents.EQUIPMENT_LEVEL.get(), level);
        }
        target.getInventory().placeItemBackInInventory(stack);

        final EquipmentRarity finalRarity = rarity;
        // itemId 是 Identifier，需 toString() 才能作为 translatable 参数（仅接受 String/Number/Boolean/Component）
        final String itemIdStr = itemId.toString();
        context.getSource().sendSuccess(() -> Component.translatable("rpgcraft.equipment.give",
                count, itemIdStr, target.getName(), finalRarity.name().toLowerCase(), level), true);
        return count;
    }

    /**
     * 按名称（不区分大小写）匹配稀有度枚举，未匹配返回 {@code null}（调用方据失败提示）。
     */
    private static EquipmentRarity matchRarity(String name) {
        if (name == null) return null;
        for (EquipmentRarity r : EquipmentRarity.values()) {
            if (r.name().equalsIgnoreCase(name)) {
                return r;
            }
        }
        return null;
    }
}
