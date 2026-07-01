package com.rpgcraft.gemstone;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * gemstone 模块命令
 * <p>
 * 提供 GM 指令生成带指定稀有度与词条的镶嵌宝石（占位物品，暂无其它获取途径）。
 * <p>
 * 命令：
 * <pre>
 * /rpg gemstone givegem &lt;宝石物品ID&gt; &lt;稀有度&gt; &lt;词条ID&gt; [词条数值] [词条ID2] [词条2数值] [词条ID3] [词条3数值] [player]
 * </pre>
 * <ul>
 *   <li><b>宝石物品ID</b>：gemstone 模块注册的宝石物品种类（当前为 {@code rpgcraftgemstone:watermelon_tourmaline}，
 *       为未来支持多种宝石预留；Tab 补全所有 gemstone 模块物品）</li>
 *   <li><b>稀有度</b>：取 {@link EquipmentRarity} 的枚举名（小写，如 blue/rainbow）</li>
 *   <li><b>词条ID</b>：必须为合法词条（可作词条的 RPG 属性，或 {@code socket_gem_affixes.json} 定义的特效词条），1~3 个</li>
 *   <li><b>词条数值</b>：可选，紧跟在对应词条ID之后；指定则覆盖默认查表值（任意整数，含负数），
 *       省略则按宝石稀有度查默认数值表。属性词条与特效词条均可指定数值</li>
 *   <li><b>player</b>：可选，省略时默认自己</li>
 * </ul>
 * 数值参数（{@code IntegerArgumentType}）与词条ID参数（{@code IdentifierArgument}）类型不同，
 * Brigadier 据此自动区分，无需引号。{@code player} 可跟在任意合法终止点之后。
 * <p>
 * 生成的宝石物品携带 {@code GEM_INSTANCE} 组件，记录其稀有度、词条与自定义数值；在铁砧中作为材料锻造
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

        // === /rpg gemstone givegem <宝石物品ID> <稀有度> <词条ID> [词条数值] [词条ID2] [词条2数值] ... [player] ===
        // 词条与数值交替出现：每个词条ID后可选跟一个数值参数。
        // 用 affixValueChain() 递归构建「词条1 → [数值1] → 词条2 → [数值2] → 词条3 → [数值3]」的参数链，
        // 每个可终止节点都允许接 [player]（作用于他人）或省略（默认自己）。
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
                                                .then(affixValueChain(1))
                                        )
                                )
                        )
                )
        );
    }

    /**
     * 递归构建「词条 + 可选数值」参数链。
     * <p>
     * 第 {@code index} 个词条（affixN）后：
     * <ul>
     *   <li>当前节点可执行（已收集 1~3 个词条）—— 默认作用于自己</li>
     *   <li>可选接 {@code [player]}（作用于他人）</li>
     *   <li>可选接数值 {@code valueN}（覆盖该词条默认值）—— 数值后又可接下一个词条或执行</li>
     *   <li>若还未到第 3 个词条，可选接 {@code affix(N+1)} 继续链</li>
     * </ul>
     * 数值参数（IntegerArgumentType）与词条ID（IdentifierArgument）/player（EntityArgument）类型互不相同，
     * Brigadier 据此自动区分，无歧义。
     *
     * @param index 当前词条序号（1-based）
     */
    private static ArgumentBuilder<CommandSourceStack, ?> affixValueChain(int index) {
        String affixArg = "affix" + index;
        String valueArg = "value" + index;

        // 词条N 节点：补全所有合法词条
        ArgumentBuilder<CommandSourceStack, ?> affixNode = Commands.argument(affixArg, IdentifierArgument.id())
                .suggests((context, builder) -> {
                    SocketGemConfig.getAllAffixIds()
                            .forEach(id -> builder.suggest(id.toString()));
                    return builder.buildFuture();
                });

        // 在词条N 节点下：当前可作为终止点（执行）—— 默认自己
        affixNode.executes(context -> executeGiveGem(context, context.getSource().getPlayerOrException()));
        // [player] 可接在词条N 后
        affixNode.then(Commands.argument("player", EntityArgument.player())
                .executes(context -> executeGiveGem(context, EntityArgument.getPlayer(context, "player"))));

        // 词条N 后可选接数值 valueN
        ArgumentBuilder<CommandSourceStack, ?> valueNode = Commands.argument(valueArg, IntegerArgumentType.integer());
        // 数值N 后可作为终止点（执行）—— 默认自己
        valueNode.executes(context -> executeGiveGem(context, context.getSource().getPlayerOrException()));
        // 数值N 后可接 [player]
        valueNode.then(Commands.argument("player", EntityArgument.player())
                .executes(context -> executeGiveGem(context, EntityArgument.getPlayer(context, "player"))));

        // 数值N 后若未达词条上限，可继续接下一个词条
        if (index < GemInstance.MAX_AFFIXES) {
            valueNode.then(affixValueChain(index + 1));
        }
        affixNode.then(valueNode);

        // 词条N 后若未达词条上限（且未指定数值），也可直接接下一个词条
        if (index < GemInstance.MAX_AFFIXES) {
            affixNode.then(affixValueChain(index + 1));
        }

        return affixNode;
    }

    /**
     * 执行生成镶嵌宝石命令。
     * <p>
     * 从命令参数解析：宝石物品 ID、稀有度、1~3 个 affixId 及其可选自定义数值、目标玩家。校验后构造
     * {@link GemInstance}（含自定义数值覆盖表）写入 {@code GEM_INSTANCE} 组件，发放指定宝石物品到目标玩家背包。
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

        // 收集词条（1~3 个）及其可选自定义数值
        List<Identifier> affixIds = new ArrayList<>();
        Map<Identifier, Integer> customValues = new LinkedHashMap<>();
        for (int index = 1; index <= GemInstance.MAX_AFFIXES; index++) {
            String affixArg = "affix" + index;
            Identifier affixId;
            try {
                affixId = IdentifierArgument.getId(context, affixArg);
            } catch (IllegalArgumentException e) {
                break; // 该词条参数不存在（链到此为止）
            }
            // 校验 affix 合法
            if (SocketGemConfig.getAffixType(affixId) == null) {
                context.getSource().sendFailure(Component.translatable(
                        "rpgcraft.gemstone.givegem.unknown_affix", affixId.toString()));
                return 0;
            }
            affixIds.add(affixId);
            // 解析可选自定义数值
            String valueArg = "value" + index;
            try {
                int customValue = IntegerArgumentType.getInteger(context, valueArg);
                customValues.put(affixId, customValue);
            } catch (IllegalArgumentException e) {
                // 该词条未指定数值，走默认查表（不写入 customValues）
            }
        }
        if (!GemInstance.isValidAffixCount(affixIds.size())) {
            context.getSource().sendFailure(Component.translatable(
                    "rpgcraft.gemstone.givegem.affix_count", GemInstance.MIN_AFFIXES, GemInstance.MAX_AFFIXES));
            return 0;
        }

        // 构造宝石实例并发放
        GemInstance gem = new GemInstance(rarity, affixIds, customValues);
        ItemStack stack = new ItemStack(gemItem);
        stack.set(RPGComponents.GEM_INSTANCE.get(), gem);
        target.getInventory().placeItemBackInInventory(stack);

        final EquipmentRarity finalRarity = rarity;
        final int affixCount = affixIds.size();
        final int customCount = customValues.size();
        final String gemIdStr = gemItemId.toString();
        context.getSource().sendSuccess(() -> Component.translatable(
                "rpgcraft.gemstone.givegem", gemIdStr, target.getName(),
                finalRarity.name().toLowerCase(), affixCount, customCount), true);
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
