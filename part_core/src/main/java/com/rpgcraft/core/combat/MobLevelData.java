package com.rpgcraft.core.combat;

/**
 * 怪物等级数据附件
 * <p>
 * 存储怪物的实际等级。不序列化——怪物死亡后不需要保留。
 * <p>
 * 默认值为 0（表示"未设置"），由 {@link CombatEventHandler#onEntityJoinLevel} 在生成时
 * 设置为配置的默认等级，或由 {@code /rpg spawn} 指令设置为指定等级。
 */
public class MobLevelData {

    /** 怪物等级，0 表示未设置 */
    private int level;

    public MobLevelData() {
        this.level = 0;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(0, level);
    }

    /** 是否已设置等级（level > 0） */
    public boolean isSet() {
        return level > 0;
    }
}
