package com.rpgcraft.core.preference;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 玩家偏好设置附件
 * <p>
 * 存储每玩家的个性化开关状态，通过 NeoForge Attachment 系统持久化保存。
 * <p>
 * 当前包含的设置：
 * <ul>
 *   <li>{@link #hudEnabled} — HUD 开关（属性面板 + 准星提示），默认启用</li>
 *   <li>{@link #combatLogEnabled} — 战斗日志开关，默认关闭</li>
 * </ul>
 * <p>
 * 注册在 core 的 {@link com.rpgcraft.core.attribute.AttributeManager#PLAYER_PREFERENCES} 中，
 * 由 client 和 combat 模块通过附件读取/写入。
 */
public class PlayerPreferences {

    /** HUD 开关（属性面板 + 准星提示），默认启用 */
    private boolean hudEnabled;

    /** 战斗日志开关，默认关闭 */
    private boolean combatLogEnabled;

    /**
     * 序列化/反序列化编解码器
     * <p>
     * 使用 optionalFieldOf 使新增字段时兼容旧存档（缺失字段使用默认值）。
     */
    public static final MapCodec<PlayerPreferences> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.BOOL.optionalFieldOf("hud_enabled", true).forGetter(PlayerPreferences::isHudEnabled),
                    Codec.BOOL.optionalFieldOf("combat_log_enabled", false).forGetter(PlayerPreferences::isCombatLogEnabled)
            ).apply(instance, PlayerPreferences::new)
    );

    /**
     * 默认构造函数（新建玩家时使用）
     */
    public PlayerPreferences() {
        this.hudEnabled = true;
        this.combatLogEnabled = false;
    }

    /**
     * 反序列化构造函数
     */
    public PlayerPreferences(boolean hudEnabled, boolean combatLogEnabled) {
        this.hudEnabled = hudEnabled;
        this.combatLogEnabled = combatLogEnabled;
    }

    // === HUD 开关 ===

    public boolean isHudEnabled() {
        return hudEnabled;
    }

    public void setHudEnabled(boolean enabled) {
        this.hudEnabled = enabled;
    }

    // === 战斗日志开关 ===

    public boolean isCombatLogEnabled() {
        return combatLogEnabled;
    }

    public void setCombatLogEnabled(boolean enabled) {
        this.combatLogEnabled = enabled;
    }
}
