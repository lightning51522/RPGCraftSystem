package com.rpgcraft.client.tooltip;

import com.rpgcraft.core.ui.TooltipImageData;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/**
 * RPG tooltip 图像组件（数据载体）
 * <p>
 * 实现 vanilla 的 {@link TooltipComponent} marker 接口，作为 client 通用渲染器
 * {@link RpgTooltipImageClientComponent} 的数据来源。由 {@code RenderTooltipEvent.GatherComponents}
 * 监听器（见 {@link RpgTooltipEventHandler}）从 {@link TooltipImageContributorCoordinator} 收集数据后
 * 构造，并注入到物品的 tooltip 元素列表中渲染。
 * <p>
 * <b>数据-渲染分离</b>：本类只承载 {@link TooltipImageData} 列表（来自各贡献者，如宝石模块），
 * 不含任何渲染逻辑。client 的 {@link RpgTooltipImageClientComponent} 读取本数据并绘制 ——
 * 这样贡献者模块（如 gemstone）只依赖 core 即可贡献数据，client 模块不依赖它们。
 *
 * @param entries 来自各 tooltip 图像贡献者的数据列表（如装备上的镶嵌槽数据）
 * @see RpgTooltipImageClientComponent 对应的客户端渲染器
 */
public record RpgTooltipImageComponent(List<TooltipImageData> entries) implements TooltipComponent {
}
