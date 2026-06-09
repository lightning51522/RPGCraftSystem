package com.rpgcraft.core.snapshot;

/**
 * 死亡属性恢复模式
 * <p>
 * 控制玩家重生时属性值的恢复策略：
 * <ul>
 *   <li>{@link #SNAPSHOT} — 快照模式：直接恢复死亡时的属性快照（包含装备加成）。
 *       当装备在死亡时掉落时，恢复的属性值仍包含已掉落装备的加成。</li>
 *   <li>{@link #RESCAN} — 重扫模式：从快照中剥离死亡时的装备加成得到基础值，
 *       再根据重生后身上实际装备重新计算属性。装备掉落后不再提供加成。</li>
 * </ul>
 * <p>
 * 可通过 {@code /rpg deathmode <snapshot|rescan>} 指令在运行时切换。
 */
public enum DeathRestoreMode {

    /** 快照模式：恢复死亡时的完整属性快照 */
    SNAPSHOT("snapshot", "快照模式"),

    /** 重扫模式：剥离旧装备加成，根据当前装备重新计算属性 */
    RESCAN("rescan", "装备重扫模式");

    /** 指令参数名（英文小写） */
    private final String commandKey;

    /** 显示名称（中文） */
    private final String displayName;

    DeathRestoreMode(String commandKey, String displayName) {
        this.commandKey = commandKey;
        this.displayName = displayName;
    }

    /** 获取指令参数名 */
    public String getCommandKey() {
        return commandKey;
    }

    /** 获取显示名称 */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 从指令参数名解析模式
     *
     * @param key 指令参数名（如 "snapshot"、"rescan"）
     * @return 对应的模式，无匹配返回 null
     */
    public static DeathRestoreMode fromCommandKey(String key) {
        for (DeathRestoreMode mode : values()) {
            if (mode.commandKey.equalsIgnoreCase(key)) {
                return mode;
            }
        }
        return null;
    }

    // ====================================================================
    // 全局模式状态
    // ====================================================================

    /** 当前生效的死亡恢复模式 */
    private static DeathRestoreMode currentMode = SNAPSHOT;

    /** 获取当前模式 */
    public static DeathRestoreMode getCurrentMode() {
        return currentMode;
    }

    /** 设置当前模式 */
    public static void setCurrentMode(DeathRestoreMode mode) {
        currentMode = mode;
    }
}
