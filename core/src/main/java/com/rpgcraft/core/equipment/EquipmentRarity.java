package com.rpgcraft.core.equipment;

/**
 * 装备稀有度
 * <p>
 * 六个等级：普通 → 精良 → 稀有 → 罕见 → 传说 → 神话。
 * 每个等级对应一个显示名称和 Minecraft 格式化颜色代码，用于 tooltip 渲染。
 */
public enum EquipmentRarity {

    COMMON("普通", "§f"),
    UNCOMMON("精良", "§a"),
    RARE("稀有", "§9"),
    EPIC("罕见", "§5"),
    LEGENDARY("传说", "§6"),
    MYTHIC("神话", "§c");

    private final String displayName;
    private final String colorCode;

    EquipmentRarity(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    /**
     * 根据名称查找稀有度（不区分大小写）
     *
     * @param name 稀有度名称（如 "rare"、"稀有"）
     * @return 对应的稀有度，未匹配返回 {@link #COMMON}
     */
    public static EquipmentRarity fromName(String name) {
        if (name == null) return COMMON;
        for (EquipmentRarity r : values()) {
            if (r.name().equalsIgnoreCase(name) || r.displayName.equals(name)) {
                return r;
            }
        }
        return COMMON;
    }
}
