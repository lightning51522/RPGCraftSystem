package com.rpgcraft.core.equipment;

/**
 * 装备对单个属性的加成值（纯整数）
 *
 * @param value 加成数值
 */
public record EquipmentBonus(int value) {

    public static final EquipmentBonus ZERO = new EquipmentBonus(0);

    public EquipmentBonus add(EquipmentBonus other) {
        return new EquipmentBonus(this.value + other.value);
    }
}
