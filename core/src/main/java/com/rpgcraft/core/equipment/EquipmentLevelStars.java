package com.rpgcraft.core.equipment;

/**
 * 装备等级的星形展示。
 * <p>
 * 等级 0~6，展示规则（最多 3 个星位）：
 * <ul>
 *   <li>L0：无星</li>
 *   <li>L1~L3：1~3 个空心星 ☆</li>
 *   <li>L4~L6：从左到右依次把空心星变为实心星 ★（L4=★☆☆，L5=★★☆，L6=★★★）</li>
 * </ul>
 * 字符 {@code ★}(U+2605) 与 {@code ☆}(U+2606) 在原版默认字体中存在，可直接渲染。
 * <p>
 * 放在 core 供 client（tooltip / HUD）与 equipment（铁砧判定无需此 helper，但共享语义）共用。
 *
 * @see RPGComponents#EQUIPMENT_LEVEL
 */
public final class EquipmentLevelStars {

    /** 实心星。 */
    public static final String FILLED = "★";
    /** 空心星。 */
    public static final String HOLLOW = "☆";

    private EquipmentLevelStars() {
    }

    /**
     * 生成指定等级的星形后缀字符串。
     *
     * @param level 装备等级（0~6；越界按 0 或 6 钳制）
     * @return 星形字符串（如 ""、"☆"、"☆☆"、"★★★"）
     */
    public static String stars(int level) {
        if (level <= 0) return "";
        if (level > 6) level = 6;
        if (level <= 3) {
            return HOLLOW.repeat(level);
        }
        // L4~L6：前 (level-3) 个实心 + 剩余空心，共 3 位
        int filled = level - 3;
        int hollow = 3 - filled;
        return FILLED.repeat(filled) + HOLLOW.repeat(hollow);
    }
}
