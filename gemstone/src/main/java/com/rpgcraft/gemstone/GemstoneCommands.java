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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
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
 * /rpg gemstone givegem &lt;宝石物品ID&gt; &lt;稀有度&gt; &lt;词条ID&gt; [词条ID2] [词条ID3] [player]
 * </pre>
 * <ul>
 *   <li><b>宝石物品ID</b>：gemstone 模块注册的宝石物品种类（当前为 {@code rpgcraftgemstone:watermelon_tourmaline}，
 *       为未来支持多种宝石预留；Tab 补全所有 gemstone 模块物品）</li>
 *   <li><b>稀有度</b>：取 {@link EquipmentRarity} 的枚举名（小写，如 blue/rainbow）</li>
 *   <li><b>词条ID</b>：必须已在 {@code socket_gem_affixes.json} 中定义（属性或特效词条均可），1~3 个</li>
 *   <li><b>player</b>：可选，省略时默认自己</li>
 * </ul>
 * 生成的宝石物品携带 {@code GEM_INSTANCE} 组件，记录其稀有度与词条；在铁砧中作为材料锻造
 * 装备时，{@link SocketGemForgeHandler} 会将其镶嵌到装备上。
 * <p>
 * <b>注意</b>：创造栏提供的是「裸」宝石（无 GEM_INSTANCE 组件），无法镶嵌；必须用本指令生成的
 * 带词条宝石才能在铁砧中镶嵌。
 */
@EventBusSubscriber(modid = GemstoneMod.MODID)
public class GemstoneCommands {

    private GemstoneCommands() {
        // 禁止实例化
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // === /rpg gemstone givegem <宝石物品ID> <稀有度> <词条ID> [词条ID2] [词条ID3] [player] ===
        // 参数链：宝石ID → 稀有度 → 词条1 → 词条2? → 词条3? → player?
        //        （player 也可跟在词条1、词条2、词条3 之后任意位置，省略时默认自己）
        dispatcher.register(Commands.literal("rpg")
                .then(Commands.literal("gemstone")
                        .then(Commands.literal("givegem")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("gem", IdentifierArgument.id())
                                        .suggests((context, builder) -> {
                                            // Tab 补全 gemstone 模块注册的所有宝石物品
                                            GemstoneItems.DEFERRED_REGISTER.getEntries()
                                                    .forEach(holder -> builder.suggest(
                                                            holder.getId().toString()));
                                            return builder.buildFuture();
                                        })
                                        .then(Commands.argument("rarity", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (EquipmentRarity r : EquipmentRarity.values()) {
                                                        if (r == EquipmentRarity.RAINBOW) continue; // RAINBOW 暂时屏蔽
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
                                                        // [player] 可跟在 affix1 后（只 1 个词条时）
                                                        .executes(context -> executeGiveGem(context,
                                                                context.getSource().getPlayerOrException()))
                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                .executes(context -> executeGiveGem(context,
                                                                        EntityArgument.getPlayer(context, "player"))))
                                                        .then(Commands.argument("affix2", IdentifierArgument.id())
                                                                .suggests((context, builder) -> {
                                                                    SocketGemConfig.getAllAffixIds()
                                                                            .forEach(id -> builder.suggest(id.toString()));
                                                                    return builder.buildFuture();
                                                                })
                                                                // [player] 可跟在 affix2 后（2 个词条时）
                                                                .executes(context -> executeGiveGem(context,
                                                                        context.getSource().getPlayerOrException()))
                                                                .then(Commands.argument("player", EntityArgument.player())
                                                                        .executes(context -> executeGiveGem(context,
                                                                                EntityArgument.getPlayer(context, "player"))))
                                                                .then(Commands.argument("affix3", IdentifierArgument.id())
                                                                        .suggests((context, builder) -> {
                                                                            SocketGemConfig.getAllAffixIds()
                                                                                    .forEach(id -> builder.suggest(id.toString()));
                                                                            return builder.buildFuture();
                                                                        })
                                                                        // 3 个词条（无 player）
                                                                        .executes(context -> executeGiveGem(context,
                                                                                context.getSource().getPlayerOrException()))
                                                                        // 3 个词条 + player
                                                                        .then(Commands.argument("player", EntityArgument.player())
                                                                                .executes(context -> executeGiveGem(context,
                                                                                        EntityArgument.getPlayer(context, "player")))
                                                                        )
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
     * 执行生成镶嵌宝石命令。
     * <p>
     * 从命令参数解析：宝石物品 ID、稀有度、1~3 个 affixId、目标玩家。校验后构造 {@link GemInstance}
     * 写入 {@code GEM_INSTANCE} 组件，发放指定宝石物品到目标玩家背包。
     *
     * @param context 命令上下文
     * @param target  目标玩家
     * @return 1 成功；0 失败（物品ID非宝石/稀有度非法/词条数量超限/affix未定义）
     */
    private static int executeGiveGem(CommandContext<CommandSourceStack> context, ServerPlayer target)
            throws CommandSyntaxException {
        // 校验宝石物品 ID（必须是 gemstone 模块注册的宝石物品）
        Identifier gemItemId = IdentifierArgument.getId(context, "gem");
        Item gemItem = BuiltInRegistries.ITEM.getOptional(gemItemId).orElse(null);
        if (gemItem == null || !isGemstoneItem(gemItem)) {
            context.getSource().sendFailure(Component.translatable(
                    "rpgcraft.gemstone.givegem.unknown_gem", gemItemId.toString()));
            return 0;
        }

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
        ItemStack stack = new ItemStack(gemItem);
        stack.set(RPGComponents.GEM_INSTANCE.get(), gem);
        target.getInventory().placeItemBackInInventory(stack);

        final EquipmentRarity finalRarity = rarity;
        final int affixCount = affixIds.size();
        final String gemIdStr = gemItemId.toString();
        context.getSource().sendSuccess(() -> Component.translatable(
                "rpgcraft.gemstone.givegem", gemIdStr, target.getName(),
                finalRarity.name().toLowerCase(), affixCount), true);
        return 1;
    }

    /**
     * 判断物品是否为 gemstone 模块注册的宝石物品。
     * <p>
     * 通过比对 gemstone 模块 DeferredRegister 的注册条目，确保指令只接受本模块的宝石物品
     * （未来新增宝石种类自动纳入，无需改本方法）。
     */
    private static boolean isGemstoneItem(Item item) {
        for (var holder : GemstoneItems.DEFERRED_REGISTER.getEntries()) {
            if (holder.get() == item) return true;
        }
        return false;
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
