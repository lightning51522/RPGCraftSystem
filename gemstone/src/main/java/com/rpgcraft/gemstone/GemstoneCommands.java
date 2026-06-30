package com.rpgcraft.gemstone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.GemInstance;
import com.rpgcraft.core.equipment.RPGComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * gemstone 模块命令
 * <p>
 * 提供 GM 指令生成带指定稀有度与词条的镶嵌宝石（占位物品，暂无其它获取途径）。
 * <p>
 * 命令：
 * <pre>
 * /rpg gemstone givegem &lt;稀有度&gt; &lt;词条ID&gt; [词条ID2] [词条ID3] [player]
 * </pre>
 * 稀有度取 {@link EquipmentRarity} 的枚举名（小写）。词条 ID 必须已在
 * {@code socket_gem_affixes.json} 中定义（属性或特效词条均可）。每颗宝石 1~3 个词条。
 * player 可选，省略时默认自己。
 * <p>
 * 生成的宝石物品携带 {@code GEM_INSTANCE} 组件，记录其稀有度与词条；在铁砧中作为材料锻造
 * 装备时，{@link SocketGemForgeHandler} 会将其镶嵌到装备上。
 */
@EventBusSubscriber(modid = GemstoneMod.MODID)
public class GemstoneCommands {

    private GemstoneCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // === /rpg gemstone givegem <稀有度> <词条ID> [词条ID2] [词条ID3] [player] ===
        dispatcher.register(Commands.literal("rpg")
                .then(Commands.literal("gemstone")
                        .then(Commands.literal("givegem")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (EquipmentRarity r : EquipmentRarity.values()) {
                                                builder.suggest(r.name().toLowerCase());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("affix1", IdentifierArgument.id())
                                                .suggests((context, builder) -> {
                                                    SocketGemConfig.getAllAffixIds()
                                                            .forEach(id -> builder.suggest(id.toString()));
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> executeGiveGem(context,
                                                        context.getSource().getPlayerOrException()))
                                                .then(Commands.argument("affix2", IdentifierArgument.id())
                                                        .suggests((context, builder) -> {
                                                            SocketGemConfig.getAllAffixIds()
                                                                    .forEach(id -> builder.suggest(id.toString()));
                                                            return builder.buildFuture();
                                                        })
                                                        .executes(context -> executeGiveGem(context,
                                                                context.getSource().getPlayerOrException()))
                                                        .then(Commands.argument("affix3", IdentifierArgument.id())
                                                                .suggests((context, builder) -> {
                                                                    SocketGemConfig.getAllAffixIds()
                                                                            .forEach(id -> builder.suggest(id.toString()));
                                                                    return builder.buildFuture();
                                                                })
                                                                .executes(context -> executeGiveGem(context,
                                                                        context.getSource().getPlayerOrException()))
                                                                .then(Commands.argument("player", EntityArgument.player())
                                                                        .executes(context -> executeGiveGem(context,
                                                                                EntityArgument.getPlayer(context, "player")))
                                                                )
                                                        )
                                                )
                                                .then(Commands.argument("player", EntityArgument.player())
                                                        .executes(context -> executeGiveGem(context,
                                                                EntityArgument.getPlayer(context, "player")))
                                                )
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 执行生成镶嵌宝石命令。
     * <p>
     * 从命令参数收集稀有度与 1~3 个 affixId，校验后构造 {@link GemInstance} 写入 {@code GEM_INSTANCE}
     * 组件，发放 {@link GemstoneItems#WATERMELON_TOURMALINE} 物品到目标玩家背包。
     *
     * @param context 命令上下文
     * @param target  目标玩家
     * @return 1 成功；0 失败（稀有度非法/词条数量超限/affix 未定义）
     */
    private static int executeGiveGem(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        // 校验稀有度
        String rarityName = StringArgumentType.getString(context, "rarity");
        EquipmentRarity rarity = matchRarity(rarityName);
        if (rarity == null) {
            context.getSource().sendFailure(Component.translatable(
                    "rpgcraft.gemstone.givegem.unknown_rarity", rarityName));
            return 0;
        }

        // 收集词条（1~3 个，按存在的参数）
        List<Identifier> affixIds = new ArrayList<>();
        for (String argName : List.of("affix1", "affix2", "affix3")) {
            try {
                Identifier affixId = IdentifierArgument.getId(context, argName);
                if (affixId == null) break;
                // 校验 affix 已定义
                if (SocketGemConfig.getAffixType(affixId) == null) {
                    context.getSource().sendFailure(Component.translatable(
                            "rpgcraft.gemstone.givegem.unknown_affix", affixId.toString()));
                    return 0;
                }
                affixIds.add(affixId);
            } catch (IllegalArgumentException e) {
                // 参数不存在（可选参数链到此为止）
                break;
            }
        }
        if (!GemInstance.isValidAffixCount(affixIds.size())) {
            context.getSource().sendFailure(Component.translatable(
                    "rpgcraft.gemstone.givegem.affix_count", GemInstance.MIN_AFFIXES, GemInstance.MAX_AFFIXES));
            return 0;
        }

        // 构造宝石实例并发放
        GemInstance gem = new GemInstance(rarity, affixIds);
        ItemStack stack = new ItemStack(GemstoneItems.WATERMELON_TOURMALINE.get());
        stack.set(RPGComponents.GEM_INSTANCE.get(), gem);
        target.getInventory().placeItemBackInInventory(stack);

        final EquipmentRarity finalRarity = rarity;
        final int affixCount = affixIds.size();
        context.getSource().sendSuccess(() -> Component.translatable(
                "rpgcraft.gemstone.givegem", target.getName(),
                finalRarity.name().toLowerCase(), affixCount), true);
        return 1;
    }

    /**
     * 按名称（不区分大小写）匹配稀有度枚举，未匹配返回 {@code null}。
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
