package com.rpgcraft.equipment;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * equipment 模块自定义 {@link Item} 注册中心
 * <p>
 * 注册本模块的自定义物品。本类是项目中<b>首个</b>自定义物品的注册点。
 * <p>
 * 命名空间使用本模块 MODID（{@code rpgcraftequipment}），与 {@code EntitiesMod} 注册自定义实体类型的做法
 * 一致——模块自有的 vanilla 注册物用模块自己的 MODID（区别于附件/数据组件这类共享数据上提到
 * {@code rpgcraftcore}）。
 * <p>
 * <b>注册方式</b>：使用 {@link DeferredRegister.Items#createItems(String)} 创建专门的 Items 延迟注册器，
 * 并经 {@link DeferredRegister.Items#registerItem(String, java.util.function.Function, java.util.function.UnaryOperator)}
 * 注册。注册器会在构造 {@link Item} 前把物品 ID 注入 {@code Item.Properties}（MC 26.1 的 {@code Item}
 * 构造函数要求 {@code itemId} 已设置，否则 {@code Item.Properties.itemIdOrThrow} 抛 NPE「Item id not set」）。
 * 不能用 {@code register(name, () -> new Item(...))} —— 它跳过了 ID 注入。
 * <p>
 * 在 {@code EquipmentMod} 构造函数中通过 {@link #getDeferredRegister()} 接到 Mod 事件总线。
 */
public final class RPGItems {

    /**
     * 物品延迟注册器（{@link DeferredRegister.Items#createItems(String)} 专用工厂，
     * 返回 {@link DeferredRegister.Items}，支持 {@code registerItem} 注入物品 ID）。
     */
    public static final DeferredRegister.Items DEFERRED_REGISTER =
            DeferredRegister.createItems(EquipmentMod.MODID);

    /**
     * 稀有度宝石：在铁砧中作为材料锻造已注册的可装备物品时，按目标稀有度的配置消耗一定数量，
     * 并有几率将该物品的稀有度提升一级（每次最多 +1）。
     * <p>
     * 可堆叠 64 个。获取方式：原版「工具与实用物品」创造标签（生存模式获取途径待后续扩展）。
     *
     * @see RarityForgeHandler 锻造逻辑（AnvilUpdateEvent 预览 + AnvilCraftEvent.Post 取出掷骰）
     */
    public static final DeferredItem<Item> RARITY_GEMSTONE =
            DEFERRED_REGISTER.registerItem("rarity_gemstone",
                    props -> new Item(props.stacksTo(64)));

    public static DeferredRegister.Items getDeferredRegister() {
        return DEFERRED_REGISTER;
    }

    private RPGItems() {
    }
}
