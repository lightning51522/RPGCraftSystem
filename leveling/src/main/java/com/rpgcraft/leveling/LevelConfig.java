package com.rpgcraft.leveling;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcraft.core.level.ExpFormula;
import com.rpgcraft.core.level.api.ILevelRegistry;
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
 * 支持 {@code /reload} 热重载。
 */
@EventBusSubscriber(modid = LevelingMod.MODID)
public class LevelConfig implements ILevelRegistry {

    /** 配置文件路径（命名空间 rpgcraftcore，匹配 core 模块资源位置） */
    private static final Identifier CONFIG_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "rpg/level_config.json");

    /**
     * 默认经验表（JSON 加载失败时的兜底）
     * <p>
     * 使用平滑递增曲线公式 {@code round(50 * L^1.5)} 生成 299 项（L=1..299），
     * 最大等级 = 300。与 {@code data/rpgcraftcore/rpg/level_config.json} 一致。
     * <p>
     * 示例：1→2 需 50；10→11 需 1581；50→51 需 17678；100→101 需 50000；299→300 需 258510。
     * <p>
     * 公式由 {@link ExpFormula} 统一提供（与职业等级系统共用，避免多处拷贝漂移）。
     */
    private static final int[] DEFAULT_EXP_TABLE = ExpFormula.generateExpTable(300);

    /** 经验表快照：expTable[0] = 等级 1 升到 2 所需经验，长度 = 最大等级 - 1 */
    private volatile int[] expTable = DEFAULT_EXP_TABLE.clone();

    private static final Gson GSON = new Gson();

    /** 单例实例（由 LevelManager.init() 创建） */
    private static LevelConfig instance;

    public LevelConfig() {
        instance = this;
    }

    /**
     * 获取单例实例
     */
    public static LevelConfig getInstance() {
        return instance;
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
        if (json.size() == 0) {
            expTable = DEFAULT_EXP_TABLE.clone();
            LevelingMod.LOGGER.info("等级经验表为空，使用默认配置（最大等级 {}）", DEFAULT_EXP_TABLE.length + 1);
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
            expTable = DEFAULT_EXP_TABLE.clone();
            LevelingMod.LOGGER.warn("等级经验表无有效条目，使用默认配置（最大等级 {}）", DEFAULT_EXP_TABLE.length + 1);
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
