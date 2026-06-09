package com.rpgcraft.client;

import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端 HUD 属性面板渲染器
 * <p>
 * 负责两项渲染任务：
 * <ol>
 *   <li>自定义生命条 —— 替换原版心形血条，以进度条方式显示自定义生命值</li>
 *   <li>属性文本面板 —— 在游戏主界面左上角以文字形式显示所有 RPG 属性值</li>
 * </ol>
 * <p>
 * 使用 NeoForge 26.1 的 {@link GuiLayer} 图层系统：
 * <ul>
 *   <li>通过 {@code replaceLayer} 替换原版生命条图层</li>
 *   <li>通过 {@code registerAboveAll} 注册属性文本图层</li>
 * </ul>
 * <p>
 * <h3>自定义生命条样式</h3>
 * 黑色描边的红色长方形进度条，扣减部分为灰色，条内居中显示 "当前值/最大值"。
 * 位置与原版心形血条一致。
 * <p>
 * <h3>StringBuilder 复用</h3>
 * 使用 {@code sb.setLength(0)} 重置而非每次创建新实例，减少渲染热路径上的 GC 压力。
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = ClientMod.MODID)
public class AttributeHudOverlay {

    /** 复用的 StringBuilder，避免每帧分配新实例造成 GC 压力 */
    private static final StringBuilder HUD_BUILDER = new StringBuilder(32);

    /** 生命条宽度（像素），与原版 10 颗心宽度大致匹配 */
    private static final int HEALTH_BAR_WIDTH = 90;

    /** 生命条高度（像素） */
    private static final int HEALTH_BAR_HEIGHT = 9;

    /** 边框厚度（像素） */
    private static final int BORDER = 1;

    /** 圆角半径（像素） */
    private static final int CORNER_RADIUS = 2;

    // 颜色常量（ARGB 格式）
    private static final int COLOR_BORDER = 0xFF222222;       // 深灰边框（比纯黑柔和）
    private static final int COLOR_HEALTH_TOP = 0xFFE03030;   // 红色渐变上方（亮暖红）
    private static final int COLOR_HEALTH_BOTTOM = 0xFF8B0000; // 红色渐变下方（暗红）
    private static final int COLOR_LOST = 0xFF373737;         // 深灰色（已损失生命）
    private static final int COLOR_TEXT = 0xFFFFFFFF;         // 白色文字

    // === 怪物信息准星提示 ===

    /** HUD 开关状态（控制属性面板和准星提示，不影响生命条） */
    private static boolean hudEnabled = true;

    /** 缓存的怪物实体 ID（-1 表示无缓存） */
    private static int cachedMobEntityId = -1;
    /** 缓存的怪物评级名称（枚举名，如 "NORMAL"） */
    private static String cachedMobRatingName = "NORMAL";
    /** 缓存的怪物等级 */
    private static int cachedMobLevel = 0;
    /** 缓存的怪物当前生命值 */
    private static int cachedMobCurrentHealth = 0;
    /** 缓存的怪物最大生命值 */
    private static int cachedMobMaxHealth = 0;
    /** 缓存的击杀经验值 */
    private static int cachedMobExp = 0;
    /** 上一次查询的实体 ID（用于检测准星目标变化） */
    private static int lastQueriedEntityId = -1;
    /** 查询冷却计时器（tick） */
    private static int queryCooldown = 0;
    /** 重新查询间隔（tick），每 2 秒刷新一次经验值以反映玩家等级变化 */
    private static final int QUERY_INTERVAL = 40;

    /**
     * 缓存怪物信息（供 {@link SyncMobInfoPacket} 客户端处理器调用）
     *
     * @param entityId       实体 ID
     * @param ratingName     评级枚举名称（如 "NORMAL"、"ELITE"）
     * @param level          怪物等级
     * @param currentHealth  当前生命值
     * @param maxHealth      最大生命值
     * @param exp            击杀经验值
     */
    public static void cacheMobInfo(int entityId, String ratingName, int level,
                                    int currentHealth, int maxHealth, int exp) {
        cachedMobEntityId = entityId;
        cachedMobRatingName = ratingName;
        cachedMobLevel = level;
        cachedMobCurrentHealth = currentHealth;
        cachedMobMaxHealth = maxHealth;
        cachedMobExp = exp;
    }

    /**
     * 设置 HUD 开关状态（供 {@link ToggleCrosshairPacket} 客户端处理器调用）
     *
     * @param enabled true = 启用 HUD（属性面板 + 准星提示），false = 禁用
     */
    public static void setHudEnabled(boolean enabled) {
        hudEnabled = enabled;
        if (!enabled) {
            // 禁用时清除准星缓存，避免残留显示
            cachedMobEntityId = -1;
        }
    }

    /**
     * 获取当前 HUD 开关状态
     */
    public static boolean isHudEnabled() {
        return hudEnabled;
    }

    /**
     * 客户端 Tick 事件处理 —— 检测准星指向的敌对实体并发送信息查询
     * <p>
     * 每个客户端 tick 执行一次（20 tps）：
     * <ol>
     *   <li>获取准星指向的实体（{@code mc.crosshairPickEntity}）</li>
     *   <li>仅对实现 {@link Monster} 接口的敌对实体发送查询</li>
     *   <li>当目标实体变化时立即发送查询；相同目标每 {@value #QUERY_INTERVAL} tick 重新查询一次</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            cachedMobEntityId = -1;
            lastQueriedEntityId = -1;
            return;
        }

        // HUD 禁用时不发送查询
        if (!hudEnabled) return;

        Entity target = mc.crosshairPickEntity;

        // 非敌对实体：清除缓存，不发送查询
        if (!(target instanceof Monster)) {
            cachedMobEntityId = -1;
            lastQueriedEntityId = -1;
            return;
        }

        int targetId = target.getId();

        // 目标变化时立即查询，或冷却结束时重新查询（反映玩家等级变化）
        if (targetId != lastQueriedEntityId || queryCooldown <= 0) {
            QueryMobInfoPacket packet = new QueryMobInfoPacket(targetId);
            mc.getConnection().send(packet);
            lastQueriedEntityId = targetId;
            queryCooldown = QUERY_INTERVAL;
        }

        queryCooldown--;
    }

    /**
     * HUD 图层注册回调（Mod 事件总线）
     * <p>
     * 1. 替换原版生命条图层为自定义进度条
     * 2. 将属性文本面板注册到所有图层的最上层
     */
    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        // 替换原版心形血条为自定义进度条
        event.replaceLayer(
                VanillaGuiLayers.PLAYER_HEALTH,
                AttributeHudOverlay::renderHealthBar
        );

        // 注册属性文本面板（在所有图层之上）
        event.registerAboveAll(
                Identifier.fromNamespaceAndPath("rpgcraftcore", "attribute_hud"),
                AttributeHudOverlay::renderOverlay
        );
    }

    /**
     * 自定义生命条渲染回调 —— 替换原版心形血条
     * <p>
     * 渲染一个圆角描边的红色渐变进度条：
     * <ul>
     *   <li>深灰色背景表示已损失的生命值</li>
     *   <li>红色渐变前景表示当前剩余生命值（上亮下暗，增加立体感）</li>
     *   <li>深灰描边</li>
     *   <li>居中显示 "当前值/最大值" 文字</li>
     * </ul>
     * <p>
     * 位置与原版心形血条一致：X = screenWidth/2 - 91, Y = screenHeight - 39
     *
     * @param guiGraphics 图形绘制上下文
     * @param deltaTracker 帧间时间增量
     */
    private static void renderHealthBar(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        EntityAttribute lifeAttr = player.getData(AttributeManager.LIFE);
        int current = lifeAttr.getValue();
        int max = lifeAttr.getMaxValue();

        // 位置：与原版心形血条一致
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = screenWidth / 2 - 91;
        int y = screenHeight - 39;

        // 1. 绘制圆角边框（比内容区域大一圈）
        int bx = x - BORDER;
        int by = y - BORDER;
        int bw = HEALTH_BAR_WIDTH + 2 * BORDER;
        int bh = HEALTH_BAR_HEIGHT + 2 * BORDER;
        fillRounded(guiGraphics, bx, by, bw, bh, CORNER_RADIUS + BORDER, COLOR_BORDER);

        // 2. 绘制圆角灰色背景（已损失生命部分）
        fillRounded(guiGraphics, x, y, HEALTH_BAR_WIDTH, HEALTH_BAR_HEIGHT, CORNER_RADIUS, COLOR_LOST);

        // 3. 绘制圆角红色渐变前景（当前生命部分）
        if (max > 0 && current > 0) {
            int healthWidth = Math.max(1, (int) ((float) current / max * HEALTH_BAR_WIDTH));
            fillRoundedGradient(guiGraphics, x, y, healthWidth, HEALTH_BAR_HEIGHT, CORNER_RADIUS,
                    COLOR_HEALTH_TOP, COLOR_HEALTH_BOTTOM);
        }

        // 4. 居中显示 "当前值/最大值" 文字
        HUD_BUILDER.setLength(0);
        HUD_BUILDER.append(current).append('/').append(max);
        String text = HUD_BUILDER.toString();

        int textWidth = mc.font.width(text);
        int textX = x + (HEALTH_BAR_WIDTH - textWidth) / 2;
        int textY = y + (HEALTH_BAR_HEIGHT - mc.font.lineHeight) / 2;
        guiGraphics.text(mc.font, text, textX, textY, COLOR_TEXT, true);
    }

    /**
     * 绘制圆角矩形填充
     * <p>
     * 通过组合多个矩形来近似圆角效果：
     * 中间通栏 + 左右窄条 + 四角小方块（半径 2 时仅为 1px 补丁）。
     *
     * @param g     图形上下文
     * @param x     左上角 X
     * @param y     左上角 Y
     * @param w     宽度
     * @param h     高度
     * @param r     圆角半径
     * @param color ARGB 颜色
     */
    private static void fillRounded(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 中间通栏（水平方向完整，垂直方向去掉上下圆角区域）
        g.fill(x + r, y, x + w - r, y + h, color);
        // 左侧窄条
        g.fill(x, y + r, x + r, y + h - r, color);
        // 右侧窄条
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // 四角补丁（1px 方块使圆角过渡平滑）
        g.fill(x + 1, y + 1, x + r, y + r, color);
        g.fill(x + w - r, y + 1, x + w - 1, y + r, color);
        g.fill(x + 1, y + h - r, x + r, y + h - 1, color);
        g.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, color);
    }

    /**
     * 绘制圆角垂直渐变矩形
     * <p>
     * 与 {@link #fillRounded} 相同的圆角策略，但中间通栏和左右窄条使用渐变填充，
     * 四角补丁使用上端颜色（与渐变顶部一致）。
     *
     * @param g      图形上下文
     * @param x      左上角 X
     * @param y      左上角 Y
     * @param w      宽度
     * @param h      高度
     * @param r      圆角半径
     * @param topColor    渐变上方 ARGB 颜色
     * @param bottomColor 渐变下方 ARGB 颜色
     */
    private static void fillRoundedGradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
                                            int topColor, int bottomColor) {
        // 中间通栏渐变
        g.fillGradient(x + r, y, x + w - r, y + h, topColor, bottomColor);
        // 左侧窄条渐变
        g.fillGradient(x, y + r, x + r, y + h - r, topColor, bottomColor);
        // 右侧窄条渐变
        g.fillGradient(x + w - r, y + r, x + w, y + h - r, topColor, bottomColor);
        // 四角补丁使用上方颜色
        g.fill(x + 1, y + 1, x + r, y + r, topColor);
        g.fill(x + w - r, y + 1, x + w - 1, y + r, topColor);
        g.fill(x + 1, y + h - r, x + r, y + h - 1, bottomColor);
        g.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, bottomColor);
    }

    /**
     * HUD 图层渲染回调 —— 每帧执行
     * <p>
     * 从注册中心遍历所有属性，读取客户端玩家的 Attachment 值并绘制。
     * 格式示例：
     * <ul>
     *   <li>有上限的属性：{@code "法力: 80 / 100"}</li>
     *   <li>无上限的属性：{@code "力量: 15"}</li>
     * </ul>
     *
     * @param guiGraphics 图形绘制上下文
     * @param deltaTracker 帧间时间增量（本实现未使用，但 GuiLayer 接口要求）
     */
    private static void renderOverlay(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        // HUD 禁用时跳过准星提示（生命条不受影响）
        if (!hudEnabled) return;

        // === 怪物信息准星提示 ===
        // 仅在缓存数据与当前准星实体匹配时渲染
        Entity crosshairTarget = mc.crosshairPickEntity;
        if (crosshairTarget != null && crosshairTarget.getId() == cachedMobEntityId) {
            int screenW = mc.getWindow().getGuiScaledWidth();
            int screenH = mc.getWindow().getGuiScaledHeight();

            HUD_BUILDER.setLength(0);
            // 非普通评级时显示评级前缀
            if (!"NORMAL".equals(cachedMobRatingName)) {
                try {
                    com.rpgcraft.core.combat.MobRating rating =
                            com.rpgcraft.core.combat.MobRating.valueOf(cachedMobRatingName);
                    HUD_BUILDER.append("[").append(rating.getDisplayName()).append("] ");
                } catch (IllegalArgumentException ignored) {
                    // 未知评级名称，跳过前缀
                }
            }
            HUD_BUILDER.append("等级: ").append(cachedMobLevel)
                    .append("  生命: ").append(cachedMobCurrentHealth).append('/').append(cachedMobMaxHealth)
                    .append("  经验: ").append(cachedMobExp);

            String mobInfoText = HUD_BUILDER.toString();
            int textW = mc.font.width(mobInfoText);
            int tooltipX = (screenW - textW) / 2;
            int tooltipY = screenH / 2 - 25; // 准星上方

            // 0xFFFFFF00 = ARGB 不透明黄色
            guiGraphics.text(mc.font, mobInfoText, tooltipX, tooltipY, 0xFFFFFF00, true);
        }
    }
}
