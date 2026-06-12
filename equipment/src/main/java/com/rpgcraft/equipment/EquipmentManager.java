package com.rpgcraft.equipment;

import com.rpgcraft.core.equipment.api.IEquipmentRegistry;
import com.rpgcraft.core.registry.IEquipmentSystem;
import com.rpgcraft.core.registry.RPGSystems;
import com.rpgcraft.core.equipment.api.IEquipmentHandler;
import com.rpgcraft.core.equipment.api.IEquipmentProvider;

import java.util.ServiceLoader;

/**
 * 装备模块全局门面
 * <p>
 * 保留对 {@link IEquipmentRegistry} 和 {@link IEquipmentHandler} 的静态引用，
 * 内部委托到 {@link DefaultEquipmentRegistry} 和 {@link DefaultEquipmentHandler}。
 * <p>
 * 新代码应通过 {@link IEquipmentRegistry} 和 {@link IEquipmentHandler} 接口访问。
 *
 * @apiNote 内部 API — 第三方模组应通过 {@link RPGSystems} 门面访问装备系统功能，
 *          不应直接依赖此类。
 */
public class EquipmentManager {

    private static IEquipmentRegistry registry;
    private static IEquipmentHandler handler;

    /**
     * 初始化注册中心和默认处理器
     * <p>
     * 必须在注册附件之前调用。由 {@link EquipmentMod} 构造函数调用。
     */
    public static void init() {
        registry = new DefaultEquipmentRegistry();
        handler = new DefaultEquipmentHandler(registry);

        for (IEquipmentProvider provider : ServiceLoader.load(IEquipmentProvider.class)) {
            provider.registerEquipment(registry);
        }

        // 注册到 RPGSystems 统一门面
        RPGSystems.registerAttackTypeResolver(registry::getAttackType);
        RPGSystems.registerEquipmentSystem(new IEquipmentSystem() {
            @Override
            public void restoreBonusTracking(net.minecraft.server.level.ServerPlayer player) {
                handler.restoreBonusTracking(player);
            }

            @Override
            public IEquipmentRegistry getRegistry() {
                return registry;
            }

            @Override
            public net.minecraft.resources.Identifier getConfigId() {
                return DefaultEquipmentRegistry.CONFIG_ID;
            }
        });
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
