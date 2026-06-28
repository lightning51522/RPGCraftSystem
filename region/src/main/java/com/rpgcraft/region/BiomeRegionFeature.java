package com.rpgcraft.region;

/**
 * 生物群系区域功能的全局开关（服务端单例，默认关闭）
 * <p>
 * 控制「按原版生物群系派生区域效果」是否生效。关闭时
 * {@link com.rpgcraft.region.spatial.RegionLocator} 不读生物群系、不查类别表，
 * 所有玩家的生物群系区域效果完全不生效（仅几何区域/基准）。
 * <p>
 * <h3>持久化与镜像</h3>
 * 权威状态存于 {@link RuntimeRegionSavedData}（跨重启持久化）。本类持有
 * {@code static volatile} 镜像，供 {@code RegionLocator} 每次区域查询零开销读取——
 * 避免热查询路径访问 SavedData。
 * <ul>
 *   <li>服务端启动：{@link RegionsDefinitionLoader#onServerStarted} 从 SavedData 加载镜像</li>
 *   <li>命令切换：{@code /rpg biomeregion on|off} 同时写 SavedData（持久化）与本镜像（立即生效）</li>
 * </ul>
 * 默认关闭（{@code false}）符合「默认安全」哲学：新世界/旧存档首次加载时生物群系区域不生效。
 *
 * @see RuntimeRegionSavedData#isBiomeRegionEnabled()
 */
public final class BiomeRegionFeature {

    /** 当前开关状态（默认关闭） */
    private static volatile boolean enabled = false;

    private BiomeRegionFeature() {}

    /** 开关是否开启 */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置开关状态（仅更新镜像，不持久化）
     * <p>
     * 持久化由 {@link RuntimeRegionSavedData#setBiomeRegionEnabled(boolean)} 负责，
     * 命令路径两者一起调用。
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
