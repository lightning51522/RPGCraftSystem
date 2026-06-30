package com.rpgcraft.gemstone;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.rpgcraft.core.equipment.EquipmentRarity;
import com.rpgcraft.core.equipment.GemInstance;
import com.rpgcraft.core.equipment.RPGComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * gemstone 模块客户端事件处理（仅客户端）
 * <p>
 * 两项职责：
 * <ol>
 *   <li>镜像加载 {@link SocketGemConfig}：tooltip 显示宝石词条数值时需查询数值表，但服务端 reload
 *       监听器不在客户端触发，故客户端需镜像加载同一份 JSON 到 {@link SocketGemConfig} 的静态状态。</li>
 *   <li>监听 {@link ItemTooltipEvent}，为宝石物品自身（手持时）的 tooltip 做两件事：
 *     <ul>
 *       <li><b>名称染色</b>：按 {@code GEM_INSTANCE} 组件的稀有度给物品名着色（与装备稀有度名称染色一致）</li>
 *       <li><b>显示词条</b>：列出这颗宝石的属性词条数值行 / 特效词条占位</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * <b>放在 gemstone 模块而非 client 模块的原因</b>：宝石词条数值表（{@link SocketGemConfig}）在
 * gemstone 模块，client 模块不依赖 gemstone。让宝石自己渲染自己的物品 tooltip（名称染色 + 词条），
 * 与装备 tooltip（client 模块渲染，因装备加成表在 equipment 但经门面访问）解耦。
 * <p>
 * <b>注意</b>：仅处理携带 {@code GEM_INSTANCE} 组件的宝石物品（裸宝石无组件，不染色不显示词条）。
 */
@EventBusSubscriber(modid = GemstoneMod.MODID, value = Dist.CLIENT)
public class GemstoneClientEventHandler {

    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onRegisterClientReload(AddClientReloadListenersEvent event) {
        event.addListener(SocketGemConfig.CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                return readConfig(rm);
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager rm, @NonNull ProfilerFiller p) {
                SocketGemConfig.loadFromJson(json, true);
            }
        });
    }

    private static JsonObject readConfig(ResourceManager rm) {
        var resource = rm.getResource(SocketGemConfig.CONFIG_ID);
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                return GSON.fromJson(reader, JsonObject.class);
            } catch (Exception e) {
                GemstoneMod.LOGGER.warn("客户端加载镶嵌宝石词条配置失败: {} - {}",
                        SocketGemConfig.CONFIG_ID, e.getMessage());
            }
        }
        return new JsonObject();
    }

    /**
     * 为宝石物品自身的 tooltip 显示词条 + 创造标签页归属行。
     * <p>
     * 仅当物品携带 {@code GEM_INSTANCE} 组件（即由指令生成的带词条宝石）时处理。裸宝石（无组件）
     * 不显示词条。
     * <p>
     * <b>注意</b>：名称染色（含彩虹稀有度的逐 tick 渐变）<b>不</b>在此处理 —— 它由 client 模块的
     * {@code EquipmentTooltipEventHandler.onItemTooltip} 统一负责（client 能用
     * {@code EquipmentRarityColors.resolveColor} 实现彩虹动画；本模块无法引用 client 类）。
     * 本处理器只负责本模块独有的词条数值行与创造标签页归属行。
     */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        GemInstance gem = stack.get(RPGComponents.GEM_INSTANCE.get());
        if (gem == null) return; // 非宝石物品（或裸宝石）

        List<Component> tooltip = event.getToolTip();

        // 追加词条行（属性词条「属性名 +数值」、特效词条「特殊效果」）
        // 复用 SocketGemTooltipContributor 的词条行构建逻辑，保证装备 tooltip 中宝石槽的词条行
        // 与宝石物品自身的词条行显示一致
        List<Component> affixLines = SocketGemTooltipContributor.buildAffixLines(gem);
        // 在名称行后空一行，再显示词条（提升可读性）
        if (!affixLines.isEmpty()) {
            tooltip.add(Component.literal(""));
            tooltip.addAll(affixLines);
        }

        // 创造标签页归属行：与原版创造模式取出的物品一致，在 tooltip 末尾追加所属创造标签页名称
        // （灰色斜体「RPG 宝石」）。指令生成的 ItemStack 在部分场景下不携带原版的创造标签归属信息，
        // 故此处显式补上，确保指令宝石与创造栏取出的裸宝石 tooltip 显示口径一致。
        tooltip.add(Component.translatable("creativetab.rpgcraftcore.gemstones")
                .withStyle(net.minecraft.ChatFormatting.GRAY)
                .withStyle(net.minecraft.ChatFormatting.ITALIC));
    }
}
