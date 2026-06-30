package com.rpgcraft.gemstone;

import com.rpgcraft.core.equipment.api.EquipmentBonusCoordinator;
import com.rpgcraft.core.ui.TooltipImageContributorCoordinator;

/**
 * gemstone 模块全局门面与初始化
 * <p>
 * 负责在模组初始化时把宝石系统的各组件接入 core 的扩展点 SPI，使装备模块和 client 模块
 * 能聚合宝石的加成/tooltip 贡献，而<b>无需</b>对 gemstone 模块有任何编译期依赖。
 * <p>
 * <b>接入清单</b>：
 * <ul>
 *   <li>{@link SocketGemBonusContributor} → {@link EquipmentBonusCoordinator}：
 *       让装备加成计算聚合镶嵌宝石的属性词条</li>
 *   <li>{@link SocketGemTooltipContributor} → {@link TooltipImageContributorCoordinator}：
 *       让 client 的 tooltip 渲染管线读取镶嵌槽图像数据</li>
 *   <li>{@link GemCombatEventListener} → {@code RPGEventBus}：
 *       在战斗伤害事件中触发攻击者装备宝石的特殊效果</li>
 * </ul>
 * <p>
 * 由 {@link GemstoneMod} 构造函数调用。
 *
 * @apiNote 内部 API — 第三方不应直接调用，初始化由 mod 入口完成。
 */
public final class GemstoneManager {

    private GemstoneManager() {
        // 禁止实例化
    }

    /**
     * 初始化宝石模块：注册贡献者、战斗事件监听器。
     * <p>
     * 必须在注册物品之前调用（贡献者引用物品 DeferredItem，但通过 {@code Supplier} 延迟解析，
     * 故无严格顺序；为清晰起见仍先 init 再注册物品）。由 {@link GemstoneMod} 构造函数调用。
     */
    public static void init() {
        // 注册加成贡献者：装备加成计算会聚合镶嵌宝石的属性词条
        EquipmentBonusCoordinator.register(new SocketGemBonusContributor());

        // 注册 tooltip 图像贡献者：client 的 tooltip 渲染会读取镶嵌槽图像数据
        TooltipImageContributorCoordinator.register(new SocketGemTooltipContributor());

        // 注册战斗事件监听器：在伤害事件中触发装备宝石的特殊效果
        GemCombatEventListener.register();
    }
}
