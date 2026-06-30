package com.rpgcraft.gemstone;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * gemstone 模块自定义 {@link Item} 注册中心
 * <p>
 * 命名空间使用本模块 MODID（{@code rpgcraftgemstone}），与 {@code RPGItems} 注册物品的做法一致
 * （模块自有的 vanilla 注册物用模块自己的 MODID）。
 * <p>
 * <b>注册方式</b>：使用 {@link DeferredRegister.Items#createItems(String)} 创建专门的 Items 延迟注册器，
 * 并经 {@code registerItem} 注册（注入物品 ID，避免 MC 26.1 的 NPE）。
 * <p>
 * 在 {@link GemstoneMod} 构造函数中通过 {@link #getDeferredRegister()} 接到 Mod 事件总线。
 */
public final class GemstoneItems {

    /**
     * 物品延迟注册器（{@link DeferredRegister.Items#createItems(String)} 专用工厂）。
     */
    public static final DeferredRegister.Items DEFERRED_REGISTER =
            DeferredRegister.createItems(GemstoneMod.MODID);

    /**
     * 西瓜电气石（镶嵌宝石）。
     * <p>
     * 在铁砧中作为材料（右槽）锻造已注册的可装备物品（左槽）时，将宝石镶嵌到装备上（每件装备 1 颗，
     * <b>无失败</b>）。宝石自身的稀有度与词条由 {@code GEM_INSTANCE} 组件携带。
     * <p>
     * <b>不加入创造标签页</b>，仅由 GM 指令 {@code /rpg gemstone givegem} 生成。可堆叠 64 个。
     *
     * @see SocketGemForgeHandler 镶嵌逻辑（AnvilUpdateEvent，确定性输出无随机）
     */
    public static final DeferredItem<Item> WATERMELON_TOURMALINE =
            DEFERRED_REGISTER.registerItem("watermelon_tourmaline",
                    props -> new Item(props.stacksTo(64)));

    public static DeferredRegister.Items getDeferredRegister() {
        return DEFERRED_REGISTER;
    }

    private GemstoneItems() {
    }
}
