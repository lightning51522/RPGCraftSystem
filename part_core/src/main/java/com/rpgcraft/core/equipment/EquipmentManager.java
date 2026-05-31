package com.rpgcraft.core.equipment;

import com.rpgcraft.core.equipment.api.IEquipmentHandler;
import com.rpgcraft.core.equipment.api.IEquipmentProvider;
import com.rpgcraft.core.equipment.api.IEquipmentRegistry;

import java.util.ServiceLoader;

/**
 * 装备模块全局门面
 * <p>
 * 保留对 {@link IEquipmentRegistry} 和 {@link IEquipmentHandler} 的静态引用，
 * 内部委托到 {@link DefaultEquipmentRegistry} 和 {@link DefaultEquipmentHandler}。
 * <p>
 * 新代码应通过 {@link IEquipmentRegistry} 和 {@link IEquipmentHandler} 接口访问。
 */
public class EquipmentManager {

    private static IEquipmentRegistry registry;
    private static IEquipmentHandler handler;

    /**
     * 初始化注册中心和默认处理器
     * <p>
     * 必须在注册附件之前调用。由 {@link com.rpgcraft.core.RPGCraftCore} 构造函数调用。
     */
    public static void init() {
        registry = new DefaultEquipmentRegistry();
        handler = new DefaultEquipmentHandler(registry);

        for (IEquipmentProvider provider : ServiceLoader.load(IEquipmentProvider.class)) {
            provider.registerEquipment(registry);
        }
    }

    public static IEquipmentRegistry getRegistry() {
        return registry;
    }

    public static IEquipmentHandler getHandler() {
        return handler;
    }

    /**
     * 替换装备加成处理器
     * <p>
     * 子模组可调用此方法注入自定义实现。
     *
     * @param newHandler 新的处理器实例
     */
    public static void setHandler(IEquipmentHandler newHandler) {
        handler = newHandler;
    }
}
