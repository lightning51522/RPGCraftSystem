package com.rpgcraft.core.equipment;

/**
 * 装备对单个属性的加成值（不可变值对象）
 * <p>
 * 使用 Java record 实现，天然不可变且线程安全。
 * 每个实例代表装备对某一个 RPG 属性的整数加成数值。
 * <p>
 * 典型用途：
 * <ul>
 *   <li>在 {@link com.rpgcraft.core.equipment.api.IEquipmentRegistry} 中作为加成映射的值类型</li>
 *   <li>在装备模块的 {@code DefaultEquipmentHandler} 中通过 {@link #add(EquipmentBonus)} 累加多个装备的同属性加成</li>
 * </ul>
 *
 * @param value 加成数值（正数=增益，负数=减益，0=无效果）
 */
public record EquipmentBonus(int value) {

    /**
     * 零加成哨兵常量
     * <p>
     * 用于装备模块的 {@code DefaultEquipmentHandler.applyBonusDiff} 中作为"该属性无加成"的默认值，
     * 避免空指针判断，也使 diff 计算更简洁（diff = new - old，无加成时 old={@link #ZERO}）。
     */
    public static final EquipmentBonus ZERO = new EquipmentBonus(0);

    /**
     * 将两个加成值合并为新的加成实例（溢出安全）
     * <p>
     * 不修改当前实例（record 不可变），而是返回一个包含合并值的新实例。
     * 常用于将多件装备对同一属性的加成进行累加。
     * <p>
     * 使用饱和加法防止溢出：当结果超过 {@code Integer.MAX_VALUE} 时钳制为上限，
     * 低于 {@code Integer.MIN_VALUE} 时钳制为下限。避免大量高加成装备叠加导致整数溢出
     * 产生负值。
     *
     * @param other 另一个加成实例
     * @return 合并后的新加成实例（溢出时钳制到 Integer 边界）
     */
    public EquipmentBonus add(EquipmentBonus other) {
        int result = this.value + other.value;
        // 检测溢出：符号不同不会溢出，符号相同但结果符号不同则发生了溢出
        if (((this.value ^ other.value) >= 0) && ((this.value ^ result) < 0)) {
            return this.value > 0 ? new EquipmentBonus(Integer.MAX_VALUE) : new EquipmentBonus(Integer.MIN_VALUE);
        }
        return new EquipmentBonus(result);
    }
}
