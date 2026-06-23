package com.rpgcraft.core.profession.api;

import com.rpgcraft.core.attribute.AttackType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 职业接口 —— 定义 RPG 职业的核心契约
 * <p>
 * 每个职业由一个实现本接口的 Java 类表示（通常继承 {@link AbstractProfession}），
 * 在附属模组的 {@code @Mod} 入口构造函数中通过
 * {@code RPGSystems.getProfessionRegistry().register(...)} 注册。
 * <p>
 * <h3>三类能力</h3>
 * <ul>
 *   <li><b>静态数据</b>（默认实现）：标识符、显示名、描述、类型、前置、等级上限、经验表、属性加成。
 *       见 {@link #getId}、{@link #getBaseBonusMap} 等。</li>
 *   <li><b>行为钩子</b>（可选 override）：战斗事件、生命周期事件、自定义加成公式、UI 图标与 tooltip。
 *       所有钩子均有默认空实现，子类按需覆写。</li>
 *   <li><b>装备/技能声明</b>（预留接口）：{@link #getEquipmentTypes}、{@link #getGrantedSkills}
 *       供未来对接装备/技能模块使用，本期返回空集合。</li>
 * </ul>
 *
 * <h3>职业分类</h3>
 * 职业分两类（见 {@link ProfessionType}）：
 * <ul>
 *   <li><b>主职业</b>（PRIMARY）：构成进阶树，{@link #getPrerequisite()} 链最终追溯到 {@code commoner}
 *       （平民，唯一根）。只有前置职业达到 {@link #getMaxLevel()} 满级才能进阶。</li>
 *   <li><b>副职业</b>（SECONDARY）：独立成树，{@link #getPrerequisite()} 只能指向其他副职业或为 null。
 *       副职业可被玩家选为"当前副职业"以提供被动加成，但不能做主职业。</li>
 * </ul>
 *
 * <h3>加成公式</h3>
 * 默认线性：{@code base + perLevel × (level - 1)}（见 {@link #getBonusAtLevel}）。
 * 子类可覆写 {@link #getBonusAtLevel} 实现非线性成长（同时置 {@link #hasCustomBonusFormula()} 为 true）。
 */
public interface IProfession {

    /**
     * 职业类型：决定该职业在职业树中的归属、可否做主/副职业。
     * <p>
     * <b>序列化注意</b>：本枚举按 {@code ordinal()} 序列化进网络包
     * （见 {@code SyncProfessionStatePacket}），新增值<b>必须追加在末尾</b>，
     * 不得插在中间，否则旧客户端反序列化会错位。
     */
    enum ProfessionType {
        /** 主职业：进阶树成员，链根于 commoner；可 advance / switchMain */
        PRIMARY,
        /** 副职业：独立成树；可被选为当前副职业，不可做主职业 */
        SECONDARY,
        /**
         * 复合职业：要求解锁复数主职业（均达满级）作为前置的特殊主职业。
         * <p>
         * 行为上等同于 {@link #PRIMARY} —— 可 advance / switchMain / 提供主职业加成；
         * 区别在于其 {@link #getPrerequisites()} 返回多个前置，{@link #getPrerequisite()} 返回 null。
         * 在职业面板中单独成树（通过标题栏 ⇌ 切换查看）。
         */
        COMPOUND;

        /**
         * 是否「可作为主职业」—— {@link #PRIMARY} 与 {@link #COMPOUND} 均为 true。
         * <p>
         * 集中表达「可 advance / switchMain / 做当前主职业」的判定，避免散落的
         * {@code == PRIMARY || == COMPOUND}。
         *
         * @return {@code true} 表示可作为主职业
         */
        public boolean isMainLike() {
            return this == PRIMARY || this == COMPOUND;
        }
    }

    // ==================================================================
    // 静态数据（核心契约）
    // ==================================================================

    /**
     * 获取职业的唯一标识符
     *
     * @return 职业标识符，如 {@code rpgcraftcore:warrior}
     */
    Identifier getId();

    /**
     * 获取职业的显示名称
     *
     * @return 显示名称，如 "战士"
     */
    String getDisplayName();

    /**
     * 获取职业的描述文本
     *
     * @return 描述，如 "力量提升，敏捷降低"
     */
    String getDescription();

    /**
     * 获取职业类型（主职业 / 副职业）
     *
     * @return 职业类型，默认 {@link ProfessionType#PRIMARY}
     */
    default ProfessionType getType() {
        return ProfessionType.PRIMARY;
    }

    /**
     * 判断是否为主职业
     * <p>
     * 等价于 {@code getType() == ProfessionType.PRIMARY}。
     *
     * @return {@code true} 表示主职业
     * @deprecated 使用 {@link #getType()} 替代，便于区分主/副职业
     */
    @Deprecated(since = "0.4.0-alpha", forRemoval = false)
    default boolean isPrimary() {
        return getType() == ProfessionType.PRIMARY;
    }

    /**
     * 获取进阶前置职业的标识符
     * <p>
     * 返回 null 表示该职业是树起点（无前置，如主职业树的平民、副职业树的根）。
     * 返回非 null 表示进阶自某基础职业（如狂战士返回战士 ID）。
     * <p>
     * <b>复合职业</b>（{@link ProfessionType#COMPOUND}）有多个前置，本方法返回 null，
     * 需通过 {@link #getPrerequisites()} 获取完整前置集合。
     * <p>
     * 类型约束（由注册时的校验保证）：
     * <ul>
     *   <li>主职业的 prerequisite 链最终必须追溯到 {@code commoner}</li>
     *   <li>副职业的 prerequisite 只能为 null 或指向其他副职业</li>
     *   <li>复合职业的 prerequisite 返回 null，其 {@link #getPrerequisites()} 指向多个主职业</li>
     * </ul>
     * 只有前置职业达到 {@link #getMaxLevel()} 满级才能解锁本职业。
     *
     * @return 前置职业 ID，或 null
     */
    default Identifier getPrerequisite() {
        return null;
    }

    /**
     * 获取进阶前置职业的完整集合（多前置契约）。
     * <p>
     * 单前置职业（绝大多数）返回包含单个元素的集合（即 {@link #getPrerequisite()} 包装）；
     * 复合职业返回多个前置主职业的 ID；树根职业返回空集合。
     * <p>
     * 默认实现从 {@link #getPrerequisite()} 派生，保证既有单前置职业向后兼容；
     * {@link AbstractProfession} 用显式字段覆盖此默认实现。
     * <p>
     * 解锁条件：<b>所有</b>前置职业都必须已解锁且达到其自身 {@link #getMaxLevel()} 满级。
     *
     * @return 不可变的前置职业 ID 集合，空集合表示无前置（树根）
     */
    default java.util.Set<Identifier> getPrerequisites() {
        Identifier prereq = getPrerequisite();
        return prereq == null ? java.util.Set.of() : java.util.Set.of(prereq);
    }

    /**
     * 是否为进阶职业（即有前置职业）
     */
    default boolean isAdvanced() {
        return !getPrerequisites().isEmpty();
    }

    /**
     * 获取职业等级上限
     *
     * @return 等级上限，默认 20
     */
    default int getMaxLevel() {
        return 20;
    }

    /**
     * 该职业专属经验表（可选）
     * <p>
     * 数组索引 i 对应从 {@code i+1} 级升到 {@code i+2} 级所需经验，长度应为 {@link #getMaxLevel()}-1。
     * 返回 null 表示使用全局默认公式（见 {@code ProfessionManager}）。
     *
     * @return 经验表数组，或 null 表示用全局公式
     */
    default int @Nullable [] getExpTable() {
        return null;
    }

    // ==================================================================
    // 属性加成
    // ==================================================================

    /**
     * 获取 1 级时（基础）的属性加成映射
     * <p>
     * 键为属性标识符，值为基础加成值（正增益/负减益）。应为不可变映射。
     * 默认返回空映射 —— 纯行为型职业可不提供任何数值加成。
     *
     * @return 基础加成映射
     */
    default Map<Identifier, Integer> getBaseBonusMap() {
        return Map.of();
    }

    /**
     * 获取某属性在本职业下的每级增量
     *
     * @param attrId 属性标识符
     * @return 每级增量，默认 0（即加成不随等级变化）
     */
    default int getBonusPerLevel(Identifier attrId) {
        return 0;
    }

    /**
     * 计算某属性在指定等级时的加成值
     * <p>
     * 默认公式：{@code base + perLevel × (level - 1)}
     * <p>
     * 子类可覆写本方法实现非线性成长（如指数增长、阶梯解锁）。
     * 覆写时应同时置 {@link #hasCustomBonusFormula()} 为 true 以便框架识别。
     *
     * @param attrId 属性标识符
     * @param level  职业等级（≥ 1）
     * @return 该等级下的加成值
     */
    default int getBonusAtLevel(Identifier attrId, int level) {
        int base = getBaseBonusMap().getOrDefault(attrId, 0);
        int perLevel = getBonusPerLevel(attrId);
        return base + perLevel * (Math.max(1, level) - 1);
    }

    /**
     * 标记本职业是否使用了自定义加成公式
     * <p>
     * 返回 true 表示子类覆写了 {@link #getBonusAtLevel}，框架可据此优化日志/调试输出。
     * 默认 false。
     *
     * @return {@code true} 如果使用了自定义公式
     */
    default boolean hasCustomBonusFormula() {
        return false;
    }

    /**
     * 获取职业提供的属性加成映射（基础值）。
     * <p>
     * 历史别名，等价于 {@link #getBaseBonusMap()}。保留以兼容现有调用方。
     *
     * @return 属性加成映射
     */
    default Map<Identifier, Integer> getBonusMap() {
        return getBaseBonusMap();
    }

    // ==================================================================
    // UI 展示（钩子）
    // ==================================================================

    /**
     * 职业面板节点图标 —— 原版物品形式（推荐）
     * <p>
     * 框架优先用本物品渲染节点图标（fakeItem，原版 advancement 节点风格）。
     * 返回 {@link ItemStack#EMPTY} 表示本职业不提供物品图标，回退到 {@link #getIconChar()}。
     *
     * @return 物品图标，默认 {@link ItemStack#EMPTY}
     */
    default ItemStack getIconItem() {
        return ItemStack.EMPTY;
    }

    /**
     * 职业面板节点图标 —— 单字符回退形式
     * <p>
     * 当 {@link #getIconItem()} 返回空时使用。默认 "?"。
     *
     * @return 单字符图标，如 "战"
     */
    default String getIconChar() {
        return "?";
    }

    /**
     * 职业面板节点悬停 tooltip 的自定义追加行
     * <p>
     * 框架在拼装完默认 tooltip（名称/类型/状态/描述/等级）后追加本方法返回的行。
     * 用于展示职业特性提示（如"战士：攻击有概率额外伤害"）。
     *
     * @param ctx tooltip 上下文（玩家在本职业的解锁/等级/激活状态）
     * @return 自定义 tooltip 行列表，默认空列表
     */
    default List<Component> getTooltip(ProfessionTooltipContext ctx) {
        return List.of();
    }

    // ==================================================================
    // 战斗钩子（由 ProfessionManager 中央调度，对玩家当前生效的职业依次调用）
    // ==================================================================

    /**
     * 玩家作为攻击者命中目标后触发
     * <p>
     * 时机：伤害已计算但尚未应用到目标 LIFE 属性前（{@code RPGDamageEvent.Pre}），
     * 允许通过修改目标属性/施加 buff 等方式追加效果。
     * <p>
     * 实现示例：战士 10% 概率额外伤害、吸血等。
     *
     * @param ctx 战斗上下文
     */
    default void onAttack(ProfessionCombatContext ctx) {
    }

    /**
     * 玩家作为被攻击者承受伤害后触发
     * <p>
     * 时机：伤害已应用到玩家 LIFE 属性后（{@code RPGDamageEvent.Post}）。
     * 实现示例：反伤、护盾回充、受击触发效果等。
     *
     * @param ctx 战斗上下文（{@code ctx.isAttacker() == false}）
     */
    default void onDamaged(ProfessionCombatContext ctx) {
    }

    /**
     * 玩家作为攻击者击杀目标后触发
     * <p>
     * 时机：致命伤害应用后（{@code RPGDamageEvent.Post} 且 lethal）。
     * 实现示例：击杀回血、击杀重置冷却等。
     *
     * @param ctx 战斗上下文
     */
    default void onKill(ProfessionCombatContext ctx) {
    }

    // ==================================================================
    // 生命周期钩子（由 ProfessionManager 在职业状态变化点调用）
    // ==================================================================

    /**
     * 本职业等级提升时触发（玩家在面板投入经验升级）
     *
     * @param ctx 生命周期上下文（level 为升级后的新等级）
     */
    default void onLevelUp(ProfessionContext ctx) {
    }

    /**
     * 玩家进阶到本职业时触发（advance 流程解锁本职业并切换为主职业）
     *
     * @param ctx 生命周期上下文
     */
    default void onAdvance(ProfessionContext ctx) {
    }

    /**
     * 本副职业被玩家激活时触发
     *
     * @param ctx 生命周期上下文
     */
    default void onActivate(ProfessionContext ctx) {
    }

    /**
     * 本副职业被玩家取消激活时触发
     *
     * @param ctx 生命周期上下文
     */
    default void onDeactivate(ProfessionContext ctx) {
    }

    /**
     * 玩家登录时触发（针对当前主职业与所有已激活副职业）
     *
     * @param ctx 生命周期上下文
     */
    default void onLogin(ProfessionContext ctx) {
    }

    /**
     * 玩家重生时触发（针对当前主职业与所有已激活副职业，加成重建后调用）
     *
     * @param ctx 生命周期上下文
     */
    default void onRespawn(ProfessionContext ctx) {
    }

    // ==================================================================
    // 装备 / 技能声明（预留接口，本期默认空实现）
    // ==================================================================

    /**
     * 本职业专属装备类型 ID 列表（预留接口）
     * <p>
     * 未来对接 equipment 模块时使用，用于声明"本职业可装备/受益于哪些装备类型"。
     * 本期返回空列表。
     *
     * @return 装备类型 ID 列表，默认空
     */
    default List<Identifier> getEquipmentTypes() {
        return List.of();
    }

    /**
     * 本职业提供的技能 ID 列表（预留接口）
     * <p>
     * 未来对接 skills 模块时使用，用于声明"本职业解锁哪些专属技能"。
     * 本期返回空列表，技能系统保持与职业解耦。
     *
     * @return 技能 ID 列表，默认空
     */
    default List<Identifier> getGrantedSkills() {
        return List.of();
    }
}
