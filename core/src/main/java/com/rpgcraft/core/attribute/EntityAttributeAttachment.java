package com.rpgcraft.core.attribute;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * 实体属性数据附件（非玩家实体专用）
 * <p>
 * 挂载到所有 {@link net.minecraft.world.entity.LivingEntity} 上的统一数据袋，
 * 存储<b>固有属性基础值</b>（如僵尸天生 15 力量）和<b>持久修饰符</b>。
 * <p>
 * 此附件为后续 Phase 的 {@code GatherAttributeEvent} 和
 * {@code AttributeSnapshotManager} 提供数据源，使非玩家实体也能通过
 * 修饰符管线动态计算属性值。
 * <p>
 * <b>与玩家路径的关系</b>：玩家继续使用现有的 13 个
 * {@link AttachmentType<EntityAttribute>} 独立附件（已稳定运行），不使用此附件。
 * <p>
 * 序列化通过 {@link #CODEC} 实现 NeoForge AttachmentType 的存档读写。
 *
 * @see com.rpgcraft.core.attribute.AttributeManager#ENTITY_ATTRIBUTE_ATTACHMENT
 */
public class EntityAttributeAttachment {

    /** 固有属性基础值：attrId → 基础值（如 rpgcraftcore:strength → 15） */
    private final Map<Identifier, Integer> intrinsicBaseValues;

    /**
     * 持久修饰符：attrId → (sourceId → 该来源的修饰符列表)
     * <p>
     * 外层按属性分组，内层按来源分组，方便按来源批量移除（如脱下装备时）。
     */
    private final Map<Identifier, Map<Identifier, List<IAttributeModifier>>> persistentModifiers;

    // ====================================================================
    // 构造函数
    // ====================================================================

    /**
     * 无参构造器（NeoForge 附件创建时调用）
     */
    public EntityAttributeAttachment() {
        this.intrinsicBaseValues = new HashMap<>();
        this.persistentModifiers = new HashMap<>();
    }

    /**
     * 反序列化构造器
     */
    private EntityAttributeAttachment(Map<Identifier, Integer> intrinsicBaseValues,
                                      Map<Identifier, Map<Identifier, List<IAttributeModifier>>> persistentModifiers) {
        this.intrinsicBaseValues = new HashMap<>(intrinsicBaseValues);
        this.persistentModifiers = new HashMap<>();
        persistentModifiers.forEach((attrId, sourceMap) ->
                this.persistentModifiers.put(attrId, new HashMap<>(sourceMap)));
    }

    // ====================================================================
    // 固有基础值 API
    // ====================================================================

    /**
     * 获取固有基础值
     *
     * @param attrId 属性标识符
     * @return 基础值，无则返回 0
     */
    public int getIntrinsicBase(Identifier attrId) {
        return intrinsicBaseValues.getOrDefault(attrId, 0);
    }

    /**
     * 设置固有基础值
     *
     * @param attrId 属性标识符
     * @param value  基础值
     */
    public void setIntrinsicBase(Identifier attrId, int value) {
        intrinsicBaseValues.put(attrId, value);
    }

    /**
     * 移除固有基础值
     *
     * @param attrId 属性标识符
     */
    public void removeIntrinsicBase(Identifier attrId) {
        intrinsicBaseValues.remove(attrId);
    }

    /**
     * 是否有指定属性的固有基础值
     *
     * @param attrId 属性标识符
     * @return true = 存在
     */
    public boolean hasIntrinsicBase(Identifier attrId) {
        return intrinsicBaseValues.containsKey(attrId);
    }

    /**
     * 获取所有固有基础值（只读快照）
     *
     * @return 不可变 Map
     */
    public Map<Identifier, Integer> getAllIntrinsicBases() {
        return Collections.unmodifiableMap(intrinsicBaseValues);
    }

    // ====================================================================
    // 持久修饰符 API
    // ====================================================================

    /**
     * 添加持久修饰符
     *
     * @param attrId   属性标识符
     * @param modifier 修饰符
     */
    public void addPersistentModifier(Identifier attrId, IAttributeModifier modifier) {
        persistentModifiers
                .computeIfAbsent(attrId, k -> new HashMap<>())
                .computeIfAbsent(modifier.getSourceId(), k -> new ArrayList<>())
                .add(modifier);
    }

    /**
     * 按来源移除指定属性的所有持久修饰符
     *
     * @param attrId   属性标识符
     * @param sourceId 来源标识符
     */
    public void removePersistentModifiers(Identifier attrId, Identifier sourceId) {
        Map<Identifier, List<IAttributeModifier>> sourceMap = persistentModifiers.get(attrId);
        if (sourceMap != null) {
            sourceMap.remove(sourceId);
            if (sourceMap.isEmpty()) {
                persistentModifiers.remove(attrId);
            }
        }
    }

    /**
     * 获取指定属性的所有持久修饰符（只读视图）
     *
     * @param attrId 属性标识符
     * @return 修饰符列表（可能为空，不为 null）
     */
    public List<IAttributeModifier> getPersistentModifiers(Identifier attrId) {
        Map<Identifier, List<IAttributeModifier>> sourceMap = persistentModifiers.get(attrId);
        if (sourceMap == null) return Collections.emptyList();
        List<IAttributeModifier> all = new ArrayList<>();
        for (List<IAttributeModifier> list : sourceMap.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * 检查指定属性是否有持久修饰符
     *
     * @param attrId 属性标识符
     * @return true = 有修饰符
     */
    public boolean hasPersistentModifiers(Identifier attrId) {
        Map<Identifier, List<IAttributeModifier>> sourceMap = persistentModifiers.get(attrId);
        return sourceMap != null && !sourceMap.isEmpty();
    }

    // ====================================================================
    // 通用 API
    // ====================================================================

    /**
     * 清空所有数据
     */
    public void clear() {
        intrinsicBaseValues.clear();
        persistentModifiers.clear();
    }

    /**
     * 附件是否为空（无基础值、无修饰符）
     *
     * @return true = 空
     */
    public boolean isEmpty() {
        return intrinsicBaseValues.isEmpty() && persistentModifiers.isEmpty();
    }

    // ====================================================================
    // 序列化
    // ====================================================================

    /**
     * 序列化辅助 record：将 {@link IAttributeModifier} 编码为可序列化的固定结构
     */
    private record SerializedModifier(Identifier source, String operation, int value) {
        static final Codec<SerializedModifier> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Identifier.CODEC.fieldOf("source").forGetter(SerializedModifier::source),
                        Codec.STRING.fieldOf("operation").forGetter(SerializedModifier::operation),
                        Codec.INT.fieldOf("value").forGetter(SerializedModifier::value)
                ).apply(instance, SerializedModifier::new)
        );
    }

    /**
     * MapCodec 序列化器，用于 NeoForge AttachmentType 的存档读写
     * <p>
     * 序列化格式：
     * <pre>{@code
     * {
     *   "intrinsic_bases": { "rpgcraftcore:strength": 15, ... },
     *   "persistent_modifiers": {
     *     "rpgcraftcore:strength": [
     *       { "source": "modid:source", "operation": "ADDITION", "value": 10 }
     *     ]
     *   }
     * }
     * }</pre>
     */
    public static final MapCodec<EntityAttributeAttachment> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    // 固有基础值：可选字段，默认空 Map
                    Codec.unboundedMap(Identifier.CODEC, Codec.INT)
                            .optionalFieldOf("intrinsic_bases", Map.of())
                            .forGetter(attachment -> Collections.unmodifiableMap(attachment.intrinsicBaseValues)),
                    // 持久修饰符：可选字段，默认空 Map
                    Codec.unboundedMap(
                            Identifier.CODEC,
                            Codec.list(SerializedModifier.CODEC)
                    ).optionalFieldOf("persistent_modifiers", Map.of())
                            .forGetter(EntityAttributeAttachment::serializeModifiers)
            ).apply(instance, EntityAttributeAttachment::deserializeFrom)
    );

    /**
     * 序列化修饰符为 Map<attrId, List<SerializedModifier>>
     */
    private Map<Identifier, List<SerializedModifier>> serializeModifiers() {
        Map<Identifier, List<SerializedModifier>> result = new HashMap<>();
        for (Map.Entry<Identifier, Map<Identifier, List<IAttributeModifier>>> attrEntry : persistentModifiers.entrySet()) {
            List<SerializedModifier> mods = new ArrayList<>();
            for (List<IAttributeModifier> modList : attrEntry.getValue().values()) {
                for (IAttributeModifier mod : modList) {
                    mods.add(new SerializedModifier(
                            mod.getSourceId(),
                            mod.getOperation().name(),
                            mod.getValue()
                    ));
                }
            }
            if (!mods.isEmpty()) {
                result.put(attrEntry.getKey(), mods);
            }
        }
        return result;
    }

    /**
     * 从序列化数据反序列化
     */
    private static EntityAttributeAttachment deserializeFrom(
            Map<Identifier, Integer> intrinsicBases,
            Map<Identifier, List<SerializedModifier>> serializedModifiers) {
        Map<Identifier, Map<Identifier, List<IAttributeModifier>>> mods = new HashMap<>();
        for (Map.Entry<Identifier, List<SerializedModifier>> attrEntry : serializedModifiers.entrySet()) {
            Map<Identifier, List<IAttributeModifier>> sourceMap = new HashMap<>();
            for (SerializedModifier sm : attrEntry.getValue()) {
                Operation op;
                try {
                    op = Operation.valueOf(sm.operation());
                } catch (IllegalArgumentException e) {
                    // 未知操作类型，跳过此修饰符
                    continue;
                }
                sourceMap
                        .computeIfAbsent(sm.source(), k -> new ArrayList<>())
                        .add(new SimpleAttributeModifier(sm.source(), op, sm.value()));
            }
            if (!sourceMap.isEmpty()) {
                mods.put(attrEntry.getKey(), sourceMap);
            }
        }
        return new EntityAttributeAttachment(intrinsicBases, mods);
    }
}
