package com.rpgcraft.leveling;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.level.api.ExpThresholdCurveManager;
import com.rpgcraft.core.level.api.ILevelRegistry;
import com.rpgcraft.core.level.api.IExpThresholdCurve;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@link ILevelRegistry} 的默认实现
 * <p>
 * 从 {@code data/rpgcraftcore/rpg/level_config.json} 读取每级升级所需增量经验，
 * 同时支持编程式注册（通过 {@link #registerExpRequirement}）。
 * <p>
 * JSON 格式（键 = 当前等级，值 = 升到下一级所需增量经验）：
 * <pre>
 * {
 *   "1": 50,
 *   "2": 141,
 *   "3": 260
 * }
 * </pre>
 * 最大等级 = 最大键值 + 1。默认经验表由公式 {@code round(50 * L^1.5)} 生成（L=1..299），
 * 最大等级 300。
 * <p>
 * <b>SPI 覆盖</b>：玩家等级阈值曲线可经 {@link ExpThresholdCurveManager#setCurve} 替换
 * （详见 {@link IExpThresholdCurve}）。当注册了自定义曲线后，{@link #loadFromJson} 会忽略
 * JSON 并改用 SPI 曲线重建表（SPI 公式级覆盖优先于 JSON 数据级微调）。
 * <p>
 * 支持 {@code /reload} 热重载。
 */
@EventBusSubscriber(modid = LevelingMod.MODID)
public class LevelConfig implements ILevelRegistry {

    /** 配置文件路径（命名空间 rpgcraftcore，匹配 core 模块资源位置） */
    private static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/level_config.json");

    /**
     * 默认最大等级（曲线/JSON 缺省时使用）。
     * <p>
     * 曲线生成 {@code expForNextLevel} 覆盖 1..{@code DEFAULT_MAX_LEVEL-1}（299 项），
     * 即从 1 级可一路升到 {@code DEFAULT_MAX_LEVEL}（300）。
     */
    static final int DEFAULT_MAX_LEVEL = 300;

    /** 经验表快照：expTable[0] = 等级 1 升到 2 所需经验，长度 = 最大等级 - 1 */
    private volatile int[] expTable;

    private static final Gson GSON = new Gson();

    /** 单例实例（由 LevelManager.init() 创建） */
    private static LevelConfig instance;

    public LevelConfig() {
        instance = this;
        // 初始表从当前生效曲线生成（此时若未注册自定义曲线，使用 core 默认 round(50×L^1.5)，
        // 与原 static-final 烘焙行为一致；若 SPI 已在更早的 mod 构造期注册，则用 SPI 曲线）。
        // 延迟到构造期而非 static-final，避免类加载时序导致 SPI override 来不及生效。
        expTable = generateTableFromCurve();
    }

    /**
     * 获取单例实例
     */
    public static LevelConfig getInstance() {
        return instance;
    }

    /**
     * 用当前生效的阈值曲线生成默认经验表（1..{@link #DEFAULT_MAX_LEVEL}-1）。
     * <p>
     * 表项 {@code table[i]} = {@code curve.expForNextLevel(i+1)}，长度 {@code DEFAULT_MAX_LEVEL - 1}。
     */
    private static int[] generateTableFromCurve() {
        IExpThresholdCurve curve = ExpThresholdCurveManager.getCurve();
        int[] table = new int[DEFAULT_MAX_LEVEL - 1];
        for (int i = 0; i < table.length; i++) {
            table[i] = curve.expForNextLevel(i + 1);
        }
        return table;
    }

    /**
     * 用当前生效曲线重建经验表（替换 {@link #expTable} 快照）。
     * <p>
     * 由两条路径调用：
     * <ul>
     *   <li>{@link #loadFromJson}：当 SPI 已接管（{@link ExpThresholdCurveManager#isOverridden()}）
     *       时忽略 JSON 并改用 SPI 曲线重建；</li>
     *   <li>{@link ExpThresholdCurveManager#setCurve} 经回调桥触发（见 core 的
     *       {@code LevelConfigHooks}）：SPI 在运行期替换曲线后立即重建表。</li>
     * </ul>
     */
    void rebuildFromCurve() {
        expTable = generateTableFromCurve();
        LevelingMod.LOGGER.info("已按阈值曲线重建玩家等级经验表（最大等级 {}）", DEFAULT_MAX_LEVEL);
    }

    /**
     * 注册资源重载监听器（Game 事件总线）
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddServerReloadListenersEvent event) {
        event.addListener(CONFIG_ID, new SimplePreparableReloadListener<JsonObject>() {
            @Override
            protected @NonNull JsonObject prepare(@NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                try {
                    var resource = resourceManager.getResource(CONFIG_ID);
                    if (resource.isPresent()) {
                        try (var reader = resource.get().openAsReader()) {
                            return GSON.fromJson(reader, JsonObject.class);
                        }
                    }
                } catch (Exception e) {
                    LevelingMod.LOGGER.error("加载等级经验表配置失败", e);
                }
                return new JsonObject();
            }

            @Override
            protected void apply(@NonNull JsonObject json, @NonNull ResourceManager resourceManager, @NonNull ProfilerFiller profiler) {
                if (instance != null) {
                    instance.loadFromJson(json);
                }
            }
        });
    }

    // ====================================================================
    // ILevelRegistry 实现
    // ====================================================================

    @Override
    public void loadFromJson(JsonObject json) {
        // SPI 优先：注册了自定义阈值曲线时，忽略 JSON 并改用 SPI 曲线重建表
        if (ExpThresholdCurveManager.isOverridden()) {
            LevelingMod.LOGGER.info("等级经验曲线已被 SPI 覆盖，忽略 level_config.json 并按曲线重建表");
            rebuildFromCurve();
            return;
        }

        if (json.size() == 0) {
            expTable = generateTableFromCurve();
            LevelingMod.LOGGER.info("等级经验表为空，使用默认曲线配置（最大等级 {}）", DEFAULT_MAX_LEVEL);
            return;
        }

        // 使用 TreeMap 自动按键排序
        TreeMap<Integer, Integer> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            try {
                int level = Integer.parseInt(entry.getKey());
                int exp = entry.getValue().getAsInt();
                if (level >= 1 && exp >= 0) {
                    sorted.put(level, exp);
                } else {
                    LevelingMod.LOGGER.warn("忽略无效等级配置: 等级={}, 经验={}", level, exp);
                }
            } catch (NumberFormatException e) {
                LevelingMod.LOGGER.warn("解析等级键失败: {}", entry.getKey());
            }
        }

        if (sorted.isEmpty()) {
            expTable = generateTableFromCurve();
            LevelingMod.LOGGER.warn("等级经验表无有效条目，使用默认曲线配置（最大等级 {}）", DEFAULT_MAX_LEVEL);
            return;
        }

        // 转换为连续数组
        int maxKey = sorted.lastKey();
        int[] newTable = new int[maxKey];
        for (int i = 1; i <= maxKey; i++) {
            if (sorted.containsKey(i)) {
                newTable[i - 1] = sorted.get(i);
            } else {
                LevelingMod.LOGGER.warn("等级 {} 缺少经验配置，使用 0", i);
                newTable[i - 1] = 0;
            }
        }

        expTable = newTable;
        LevelingMod.LOGGER.info("已加载等级经验表，最大等级 {}（{} 个等级段）", maxKey + 1, maxKey);
    }

    @Override
    public void registerExpRequirement(int level, int expRequired) {
        if (level < 1 || expRequired < 0) {
            LevelingMod.LOGGER.warn("忽略无效经验注册: 等级={}, 经验={}", level, expRequired);
            return;
        }

        int[] current = expTable;
        int needed = level; // 数组长度至少为 level（索引 level-1）
        if (needed > current.length) {
            // 扩展数组，新条目填 0
            int[] expanded = Arrays.copyOf(current, needed);
            expanded[level - 1] = expRequired;
            expTable = expanded;
        } else {
            // 在现有范围内，直接覆盖
            int[] updated = current.clone();
            updated[level - 1] = expRequired;
            expTable = updated;
        }
    }

    @Override
    public int getMaxLevel() {
        return expTable.length + 1;
    }

    @Override
    public int getExpForLevel(int level) {
        if (level < 1 || level > expTable.length) return -1;
        return expTable[level - 1];
    }
}
