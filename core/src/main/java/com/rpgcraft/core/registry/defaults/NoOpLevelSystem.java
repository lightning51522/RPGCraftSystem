package com.rpgcraft.core.registry.defaults;

import com.rpgcraft.core.registry.ILevelSystem;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link ILevelSystem} 的 no-op 兜底实现。
 * <p>
 * 无 leveling 模块时由 core 预填充：等级恒为 1、经验恒为 0、所有写操作与升级为 no-op、
 * 升级所需经验返回 -1（表示已达"最大等级"1）、同步为 no-op。
 * 首次调用记录一次 WARN，提示 leveling 模块未加载。
 *
 * @see com.rpgcraft.core.registry.RPGSystems#getLevelSystem()
 */
public final class NoOpLevelSystem implements ILevelSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger("RPGCraftCore/NoOpLevelSystem");
    private static volatile boolean warned = false;

    private static void warnOnce() {
        if (!warned) {
            synchronized (NoOpLevelSystem.class) {
                if (!warned) {
                    LOGGER.warn("leveling 模块未加载，ILevelSystem 使用 no-op 兜底（等级恒为 1，无经验/升级）");
                    warned = true;
                }
            }
        }
    }

    @Override
    public int getLevel(ServerPlayer player) {
        warnOnce();
        return 1;
    }

    @Override
    public int getExperience(ServerPlayer player) {
        warnOnce();
        return 0;
    }

    @Override
    public void setLevel(ServerPlayer player, int level) {
        warnOnce();
    }

    @Override
    public void setExperience(ServerPlayer player, int experience) {
        warnOnce();
    }

    @Override
    public boolean addExperience(ServerPlayer player, int amount) {
        warnOnce();
        return false;
    }

    @Override
    public int getExpForNextLevel(ServerPlayer player) {
        warnOnce();
        return -1; // 已达最大等级 1
    }

    @Override
    public int getExpForLevel(int level) {
        warnOnce();
        return -1; // 超出唯一等级
    }

    @Override
    public int getMaxLevel() {
        warnOnce();
        return 1;
    }

    @Override
    public void syncToClient(ServerPlayer player) {
        warnOnce();
    }
}
