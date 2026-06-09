package com.rpgcraft.equipment.api;

import com.rpgcraft.core.equipment.api.IEquipmentRegistry;

/**
 * 装备提供者 SPI
 * <p>
 * 其他模组实现此接口来注册自定义装备的属性加成。
 * 通过 NeoForge 事件机制发现并调用。
 * <p>
 * 示例：
 * <pre>
 * public class MyModEquipment implements IEquipmentProvider {
 *     public void registerEquipment(IEquipmentRegistry registry) {
 *         registry.register(
 *             Identifier.fromNamespaceAndPath("mymod", "fire_sword"),
 *             Map.of(
 *                 Identifier.fromNamespaceAndPath("rpgcraftcore", "strength"), new EquipmentBonus(20)
 *             )
 *         );
 *     }
 * }
 * </pre>
 */
public interface IEquipmentProvider {

    /**
     * 注册自定义装备的属性加成到注册中心
     *
     * @param registry 装备注册中心实例
     */
    void registerEquipment(IEquipmentRegistry registry);
}
