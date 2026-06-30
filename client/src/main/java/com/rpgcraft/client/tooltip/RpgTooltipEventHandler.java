package com.rpgcraft.client.tooltip;

import com.mojang.datafixers.util.Either;
import com.rpgcraft.client.ClientMod;
import com.rpgcraft.core.ui.TooltipImageData;
import com.rpgcraft.core.ui.TooltipImageContributorCoordinator;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.List;

/**
 * RPG tooltip 图像渲染管线（通用，不绑定具体业务）
 * <p>
 * 把外部模块（如 gemstone）通过 {@link TooltipImageContributorCoordinator} 贡献的 tooltip 图像数据，
 * 注入到任意物品的 tooltip 中渲染。这样贡献者模块只依赖 core 提供数据，client 模块统一渲染 ——
 * 删除任何贡献者模块，渲染管线照常工作（无贡献即无图像元素）。
 * <p>
 * <b>两条接入路径</b>（事件总线不同）：
 * <ul>
 *   <li>{@link RegisterClientTooltipComponentFactoriesEvent}（Mod 事件总线）：注册
 *       {@link RpgTooltipImageComponent} → {@link RpgTooltipImageClientComponent} 的工厂映射，
 *       使 vanilla tooltip 系统能识别并渲染本组件</li>
 *   <li>{@link RenderTooltipEvent.GatherComponents}（NeoForge Game 事件总线）：在收集 tooltip 元素时，
 *       调用 {@link TooltipImageContributorCoordinator#collectAll(ItemStack)}，若非空则把
 *       {@link RpgTooltipImageComponent} 追加到 tooltip 元素列表</li>
 * </ul>
 * <p>
 * <b>为何用 GatherComponents 而非 Item#getTooltipImage</b>：装备是原版物品（由 JSON 配置注册加成，
 * 非自定义 Item 子类），无法覆写 {@code getTooltipImage}。GatherComponents 事件允许为任意物品
 * 注入 TooltipComponent，无需子类化物品。
 *
 * @see RpgTooltipImageComponent        数据载体
 * @see RpgTooltipImageClientComponent  客户端渲染器
 * @see TooltipImageContributorCoordinator 贡献者协调器（数据来源）
 */
public class RpgTooltipEventHandler {

    private RpgTooltipEventHandler() {
        // 禁止实例化，通过事件订阅器调用静态方法
    }

    /**
     * 注册 TooltipComponent 工厂（Mod 事件总线，仅客户端）。
     * <p>
     * 把 {@link RpgTooltipImageComponent} 映射到其客户端渲染器
     * {@link RpgTooltipImageClientComponent}，使 vanilla tooltip 渲染系统能识别本组件类型。
     */
    @EventBusSubscriber(modid = ClientMod.MODID, value = Dist.CLIENT)
    public static class ModBus {
        @SubscribeEvent
        public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(RpgTooltipImageComponent.class, RpgTooltipImageClientComponent::new);
        }
    }

    /**
     * 注入 tooltip 图像组件（NeoForge Game 事件总线，仅客户端）。
     * <p>
     * 在 tooltip 收集元素时，调用 {@link TooltipImageContributorCoordinator#collectAll} 收集所有
     * 贡献者对该物品的图像数据；若非空，构造 {@link RpgTooltipImageComponent} 并以
     * {@code Either.right(...)} 追加到 tooltip 元素列表末尾（在所有文本行之后）。
     */
    @EventBusSubscriber(modid = ClientMod.MODID, value = Dist.CLIENT)
    public static class GameBus {
        @SubscribeEvent
        public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
            ItemStack stack = event.getItemStack();
            if (stack.isEmpty()) return;

            List<TooltipImageData> collected = TooltipImageContributorCoordinator.collectAll(stack);
            if (collected.isEmpty()) return;

            TooltipComponent component = new RpgTooltipImageComponent(collected);
            event.getTooltipElements().add(Either.right(component));
        }
    }
}
