package com.rpgcraft.core.ui;

import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Tooltip 图像贡献者 SPI
 * <p>
 * 允许外部模块（如 {@code gemstone}）为物品 tooltip 贡献<b>图像元素</b>（方形槽位 + 物品图标 +
 * 词条文本），而无需在物品类上覆写 {@code getTooltipImage}（尤其当物品是原版物品、无法子类化时）。
 * <p>
 * <b>数据-渲染分离</b>：本接口只贡献 {@link TooltipImageData}（纯数据，不含客户端类），由
 * client 模块的通用 {@code ClientTooltipComponent} 渲染器统一绘制。这样宝石模块只依赖 core
 * 即可贡献 tooltip 数据，client 模块不依赖宝石模块 —— 删除宝石模块，tooltip 渲染照常工作
 * （无贡献者即无图像元素）。
 * <p>
 * <b>注册</b>：插件模块在自己的 {@code init()} 中调用
 * {@link TooltipImageContributorCoordinator#register(ITooltipImageContributor)}，仿
 * {@code RPGUIPlugins} 的插件注册模式。
 * <p>
 * <b>故障隔离</b>：{@link TooltipImageContributorCoordinator#collectAll(ItemStack)} 已用
 * try-catch 包裹每个贡献者调用，单个贡献者抛异常不影响其它。
 *
 * @see TooltipImageData
 * @see TooltipImageContributorCoordinator
 */
public interface ITooltipImageContributor {

    /**
     * 获取贡献者的唯一标识符
     * <p>
     * 用于日志和调试，应使用模块命名空间（如 "rpgcraftgemstone:socket_gem"）。
     *
     * @return 贡献者标识符字符串
     */
    String getContributorId();

    /**
     * 为一件物品贡献 tooltip 图像数据
     * <p>
     * 由 client 模块的 tooltip 渲染管线在收集图像元素时调用。返回 {@code null} 表示该物品与本
     * 贡献者无关（如未注册为装备）。返回非 null 时，其 {@link TooltipImageData} 会被注入到
     * 该物品的 tooltip 组件列表中渲染。
     * <p>
     * 实现应<b>只读</b>地检视 {@code stack}（读 DataComponent），不应修改入参。
     *
     * @param stack 待显示 tooltip 的物品堆叠
     * @return tooltip 图像数据；{@code null} 表示不贡献
     */
    @Nullable
    TooltipImageData contribute(ItemStack stack);
}
