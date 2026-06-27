package com.rpgcraft.core.attribute;

import java.lang.Math;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeModifier;
import net.minecraft.resources.Identifier;

/**
 * {@link IAttribute} 的默认实现
 * <p>
 * 属性值通过<b>修饰符管线</b>计算，而非直接存储。核心字段：
 * <ul>
 *   <li>{@link #baseValue} —— 管线起点，由等级/种族等永久性设置</li>
 *   <li>{@link #baseMaxValue} —— 上限的管线起点</li>
 *   <li>{@link #modifiers} —— 修饰符映射（sourceId → 修饰符集合）</li>
 * </ul>
 * <p>
 * {@link #getValue()} 和 {@link #getMaxValue()} 每次调用时通过管线计算最终值，
 * 结果会被缓存直到修饰符发生变化（缓存失效机制）。
 * <p>
 * {@link #setValue(int)} 和 {@link #setMaxValue(int)} 直接修改管线计算结果，
 * 适用于战斗伤害扣除等"临时覆盖"场景。当修饰符变化时，管线会重新计算，
 * 之前的直接设置会被覆盖。
 * <p>
 * 该类同时提供 {@link #CODEC} 用于 NeoForge AttachmentType 的存档序列化。
 * <p>
 * <b>序列化策略</b>：CODEC 只序列化管线起点（基础值），不序列化计算结果或修饰符。
 * 修饰符由各模块（职业、属性点、装备）在登录时从各自附件重建。详见 {@link #CODEC}。
 */
public class EntityAttribute implements IAttribute {

    /** 属性标识符（用于管线事件） */
    private Identifier attributeId;

    /** 属性显示名称（如"生命"、"力量"等），用于 HUD 和命令显示 */
    private String name;

    /**
     * 属性基础值（管线起点）
     * <p>
     * 由等级提升、种族加成等永久性来源设置。
     * 修饰符的加成在管线中自动叠加。
     */
    private int baseValue;

    /**
     * 属性基础上限值（管线起点）
     */
    private int baseMaxValue;

    /**
     * 是否为有上限的属性
     */
    private boolean capped;

    /**
     * 重生时是否恢复到最大值
     */
    private boolean resetOnRespawn;

    /**
     * 装备加成是否影响上限
     */
    private boolean equipmentAffectsMax;

    /**
     * 修饰符存储：sourceId → 该来源下的修饰符列表
     * <p>
     * 使用 ConcurrentHashMap 保证线程安全。
     */
    private final Map<Identifier, List<IAttributeModifier>> modifiers = new ConcurrentHashMap<>();

    /**
     * 缓存的管线计算结果（currentValue）
     * <p>
     * 当 modifiers 发生变化时，缓存失效，下次 getValue() 时重新计算。
     * <p>
     * <b>线程可见性</b>：本字段与 {@link #cachedMaxValue}、{@link #cacheValid} 一并声明为
     * {@code volatile}。{@link #recalculate()} 先写入这两个值、最后置 {@code cacheValid = true}，
     * 读线程仅当 {@code cacheValid == true} 时才读取缓存值；三者均 {@code volatile} 才能保证
     * 「看到 cacheValid==true 就一定看到最新缓存值」的 happens-before 关系（仅 {@code cacheValid}
     * volatile 时，缓存值字段不保证可见性，存在读到旧值的数据竞争）。
     */
    private volatile int cachedValue;

    /**
     * 缓存的管线计算结果（maxValue）
     * <p>
     * 同 {@link #cachedValue}，声明为 {@code volatile} 以保证可见性。
     */
    private volatile int cachedMaxValue;

    /** 缓存是否有效 */
    private volatile boolean cacheValid = false;

    // ====================================================================
    // 构造函数
    // ====================================================================

    /**
     * 存档反序列化用构造函数（简化参数）
     * <p>
     * <b>参数语义为管线起点（基础值），不是计算后的最终值。</b>
     * {@link #CODEC} 通过 {@link #getBaseValue()} / {@link #getBaseMaxValue()} 写入，
     * 反序列化时回填到 {@link #baseValue} / {@link #baseMaxValue}。
     * <p>
     * <b>切勿</b>把 {@link #getValue()} / {@link #getMaxValue()}（含修饰符的计算结果）
     * 序列化后回填为基础值——否则每次退出世界再进入时，{@code reapplyAllModifiers}
     * 会在「已被污染的基础值」上再次叠加修饰符，导致数值无限膨胀。
     * <p>
     * 反序列化时 {@link #cachedValue} / {@link #cachedMaxValue} 初始化为基础值（满血状态）；
     * 登录流程随后通过 {@code reapplyAllModifiers} 重新应用修饰符使缓存失效并重算。
     */
    public EntityAttribute(String name, int currentValue, int maxValue) {
        this.name = name;
        this.baseValue = currentValue;
        this.baseMaxValue = maxValue;
        this.capped = maxValue < Integer.MAX_VALUE;
        this.cachedValue = currentValue;
        this.cachedMaxValue = maxValue;
        this.cacheValid = true;
    }

    /**
     * 完整构造函数（注册时使用）
     *
     * @param attributeId       属性标识符
     * @param name              显示名称
     * @param baseValue         基础值
     * @param baseMaxValue      基础上限值
     * @param capped            是否有上限
     * @param resetOnRespawn    重生时是否恢复满值
     * @param equipmentAffectsMax 装备是否影响上限
     */
    public EntityAttribute(Identifier attributeId, String name, int baseValue,
                           int baseMaxValue, boolean capped, boolean resetOnRespawn,
                           boolean equipmentAffectsMax) {
        this.attributeId = attributeId;
        this.name = name;
        this.baseValue = baseValue;
        this.baseMaxValue = baseMaxValue;
        this.capped = capped;
        this.resetOnRespawn = resetOnRespawn;
        this.equipmentAffectsMax = equipmentAffectsMax;
        this.cachedValue = baseValue;
        this.cachedMaxValue = baseMaxValue;
        this.cacheValid = true;
    }

    // ====================================================================
    // 属性标识符（用于管线事件）
    // ====================================================================

    /** 设置属性标识符（注册时调用） */
    public void setAttributeId(Identifier id) {
        this.attributeId = id;
    }

    /** 获取属性标识符 */
    public Identifier getAttributeId() {
        return attributeId;
    }

    // ====================================================================
    // 元数据
    // ====================================================================

    /** 是否有上限 */
    public boolean isCapped() { return capped; }

    /** 重生时是否恢复满值 */
    public boolean shouldResetOnRespawn() { return resetOnRespawn; }

    /** 装备加成是否影响上限 */
    public boolean equipmentAffectsMax() { return equipmentAffectsMax; }

    // ====================================================================
    // IAttribute 基本实现
    // ====================================================================

    @Override
    public String getName() { return this.name; }

    /** 设置显示名称 */
    public void setName(String name) { this.name = name; }

    @Override
    public int getBaseValue() {
        return baseValue;
    }

    @Override
    public void setBaseValue(int value) {
        this.baseValue = value;
        invalidateCache();
    }

    @Override
    public int getBaseMaxValue() {
        return baseMaxValue;
    }

    @Override
    public void setBaseMaxValue(int max) {
        this.baseMaxValue = max;
        invalidateCache();
    }

    /**
     * 获取属性最终值（经管线计算，带缓存）
     * <p>
     * 若缓存有效直接返回，否则执行管线计算并更新缓存。
     */
    @Override
    public int getValue() {
        if (cacheValid) {
            return cachedValue;
        }
        recalculate();
        return cachedValue;
    }

    /**
     * 直接设置属性最终值
     * <p>
     * 用于战斗伤害扣除、治疗等直接覆盖场景。
     * 设置后会更新缓存值，但不会修改基础值。
     * 当修饰符变化导致缓存失效时，此设置会被管线重新计算覆盖。
     */
    @Override
    public void setValue(int value) {
        // 直接设置缓存值，钳制到 [0, getMaxValue()]
        int max = getMaxValue();
        this.cachedValue = Math.clamp(value, 0, max);
        // 保持缓存有效（因为这是一个明确的值设置）
        this.cacheValid = true;
    }

    /**
     * 获取属性最终上限值（经管线计算，带缓存）
     */
    @Override
    public int getMaxValue() {
        if (cacheValid) {
            return cachedMaxValue;
        }
        recalculate();
        return cachedMaxValue;
    }

    /**
     * 直接设置最终上限值
     */
    @Override
    public void setMaxValue(int max) {
        this.cachedMaxValue = max;
        // 如果当前值超过新上限，钳制
        if (this.cachedValue > this.cachedMaxValue) {
            this.cachedValue = this.cachedMaxValue;
        }
        this.cacheValid = true;
    }

    @Override
    public boolean hasMaxValue() {
        return getMaxValue() < Integer.MAX_VALUE;
    }

    @Override
    public void fillMax() {
        this.cachedValue = getMaxValue();
    }

    // ====================================================================
    // 修饰符管理
    // ====================================================================

    @Override
    public void addModifier(IAttributeModifier modifier) {
        modifiers.computeIfAbsent(modifier.getSourceId(), k -> new ArrayList<>())
                .add(modifier);
        invalidateCache();
    }

    @Override
    public void removeModifier(Identifier sourceId) {
        modifiers.remove(sourceId);
        invalidateCache();
    }

    @Override
    public Collection<IAttributeModifier> getModifiers() {
        // 返回所有修饰符的扁平只读视图
        List<IAttributeModifier> all = new ArrayList<>();
        for (List<IAttributeModifier> list : modifiers.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * 获取指定来源的修饰符列表
     *
     * @param sourceId 来源标识符
     * @return 该来源的修饰符列表（可能为空）
     */
    public List<IAttributeModifier> getModifiersBySource(Identifier sourceId) {
        return modifiers.getOrDefault(sourceId, Collections.emptyList());
    }

    // ====================================================================
    // 缓存管理
    // ====================================================================

    /**
     * 使缓存失效
     * <p>
     * 在修饰符增删、基础值修改时调用。
     */
    private void invalidateCache() {
        this.cacheValid = false;
    }

    /**
     * 重新计算管线并更新缓存
     */
    private void recalculate() {
        Collection<IAttributeModifier> allMods = getModifiers();

        // 计算最终值
        if (attributeId != null) {
            this.cachedValue = AttributePipeline.compute(attributeId, baseValue, allMods);
        } else {
            // 兼容旧代码（attributeId 未设置时使用简单计算）
            this.cachedValue = simpleCompute(baseValue, allMods);
        }

        // 计算最终上限值
        if (attributeId != null) {
            this.cachedMaxValue = AttributePipeline.compute(attributeId, baseMaxValue, allMods);
        } else {
            this.cachedMaxValue = simpleCompute(baseMaxValue, allMods);
        }

        // 当前值不超过上限
        if (capped && this.cachedValue > this.cachedMaxValue) {
            this.cachedValue = this.cachedMaxValue;
        }

        this.cacheValid = true;
    }

    /**
     * 简单管线计算（无事件触发，用于 attributeId 未设置的场景）。
     * <p>
     * <b>与 {@link AttributePipeline#compute} 的关系</b>：两者实现相同的修饰符排序数学
     * （ADDITION → MULTIPLY_BASE → MULTIPLY_TOTAL → 截断），但本方法<b>故意不触发事件</b>
     * （{@code AttributePostAdditionEvent}/{@code AttributeFinalizeEvent}）也不需要 attributeId——
     * 它是 attributeId 缺失时的兜底路径。{@code AttributePipeline.compute} 需要 attributeId
     * 并发射事件，无法直接复用于此场景，故数学逻辑在此重复一份。
     */
    private static int simpleCompute(int base, Collection<IAttributeModifier> mods) {
        double value = base;
        double baseMul = 0;
        double totalMul = 0;
        for (IAttributeModifier mod : mods) {
            switch (mod.getOperation()) {
                case ADDITION -> value += mod.getValue();
                case MULTIPLY_BASE -> baseMul += mod.getValue() / 100.0;
                case MULTIPLY_TOTAL -> totalMul += mod.getValue() / 100.0;
            }
        }
        value *= (1.0 + baseMul);
        value *= (1.0 + totalMul);
        return Math.max(0, (int) value);
    }

    // ====================================================================
    // 序列化
    // ====================================================================

    /**
     * MapCodec 序列化器，用于 NeoForge AttachmentType 的存档读写
     * <p>
     * <b>序列化管线起点（基础值），不序列化计算结果，也不序列化修饰符。</b>
     * <ul>
     *   <li>基础值（baseValue / baseMaxValue）：由等级提升、种族加成等永久性来源设置，
     *       需要跨会话保留，故序列化。</li>
     *   <li>修饰符（职业加成、属性点、装备加成）：不参与序列化。它们由各自模块
     *       （{@code ProfessionManager.reapplyAllBonuses}、
     *       {@code AttributePointsManager.reapplyAllModifiers}、
     *       {@code EquipmentManager.restoreBonusTracking}）在登录时从各自附件
     *       （职业等级、属性点 allocations、当前装备）重建。</li>
     * </ul>
     * <p>
     * <b>历史教训：</b>早期版本曾通过 {@link #getValue()} / {@link #getMaxValue()}
     * 序列化管线计算结果。由于反序列化把这些值回填到 {@link #baseValue}，
     * 每次退出世界再进入时，{@code reapplyAllModifiers} 会在「已被污染的基础值」
     * 上再次叠加修饰符，导致数值随重登次数无限膨胀。本版本改为只序列化基础值。
     */
    public static final MapCodec<EntityAttribute> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.fieldOf("name").forGetter(EntityAttribute::getName),
                    Codec.INT.fieldOf("current").forGetter(EntityAttribute::getBaseValue),
                    Codec.INT.fieldOf("max").forGetter(EntityAttribute::getBaseMaxValue)
            ).apply(instance, EntityAttribute::new)
    );
}
