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
 * 提供 GM 强制设置玩家背包中指定 ID 物品的稀有度。
 * <p>
 * 命令列表：
 * <pre>
 * /rpg setrarity &lt;物品ID&gt; &lt;稀有度&gt; [player] — 设置指定玩家背包中所有该 ID 物品的稀有度
 * </pre>
 * <p>
 * 稀有度取 {@link EquipmentRarity} 的枚举名（小写，如 {@code white}/{@code blue}/{@code rainbow}），
 * 未匹配返回失败提示（不静默兜底为 GRAY）。
 */
@EventBusSubscriber(modid = EquipmentMod.MODID)
public class EquipmentCommands {

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
                    target.getName(), itemId));
        } else {
            final int count = modified;
            context.getSource().sendSuccess(() -> Component.translatable("rpgcraft.equipment.setrarity",
                    count, target.getName(), rarity.name()), true);
        }
        return modified;
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
