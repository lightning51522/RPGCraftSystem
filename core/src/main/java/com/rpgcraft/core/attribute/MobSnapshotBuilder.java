package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.attribute.GatherAttributeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/**
 * 非玩家实体属性快照构建器
 * <p>
 * 从 {@link EntityAttributeAttachment}（固有基础值 + 持久修饰符）和
 * {@link GatherAttributeEvent}（动态修饰符）合并计算属性快照。
 * <p>
 * 与 {@link DefaultAttributeRegistry#createSnapshot} 中玩家路径分离，
 * 避免污染玩家属性快照逻辑。
 * <p>
 * <b>构建流程</b>：
 * <ol>
 *   <li>触发 {@link GatherAttributeEvent}，收集模块注入的动态修饰符</li>
 *   <li>为每个已注册属性：
 *     <ul>
 *       <li>基础值 = 附件固有值，无则使用注册默认值</li>
 *       <li>修饰符 = 持久修饰符 + 动态修饰符</li>
 *       <li>通过 {@link AttributePipeline#compute} 计算最终值</li>
 *     </ul>
 *   </li>
 *   <li>构建不可变的 {@link AttributeSnapshot}</li>
 * </ol>
 * <p>
 * 此类为包内工具类，仅供 {@link DefaultAttributeRegistry} 和
 * {@link AttributeSnapshotManager} 内部调用。
 *
 * @see EntityAttributeAttachment
 * @see GatherAttributeEvent
 * @see AttributePipeline
 */
final class MobSnapshotBuilder {

    private MobSnapshotBuilder() {
    } // 禁止实例化

    /**
     * 构建非玩家实体的属性快照（单实体路径）
     * <p>
     * 触发 {@link GatherAttributeEvent} 收集动态修饰符，然后委托到
     * {@link #buildWithModifiers} 执行实际计算。
     *
     * @param entity     目标实体
     * @param registry   属性注册中心
     * @param attachment 实体属性附件（非空、非空数据）
     * @return 属性快照
     */
    static AttributeSnapshot build(LivingEntity entity,
                                   IAttributeRegistry registry,
                                   EntityAttributeAttachment attachment) {
        // 触发 GatherAttributeEvent，收集模块注入的动态修饰符
        Map<Identifier, List<IAttributeModifier>> gatheredModifiers = new LinkedHashMap<>();
        RPGEventBus.post(new GatherAttributeEvent(entity, gatheredModifiers));
        return buildWithModifiers(entity, registry, attachment, gatheredModifiers);
    }

    /**
     * 构建非玩家实体的属性快照（预收集修饰符路径）
     * <p>
     * 接受外部已收集的动态修饰符（来自 {@link GatherAttributeBatchEvent}），
     * 避免为批量场景中的每个实体重复触发单实体事件。
     * <p>
     * 此方法供 {@link AttributeSnapshotManager} 的批量路径调用。
     *
     * @param entity            目标实体
     * @param registry          属性注册中心
     * @param attachment        实体属性附件（非空、非空数据）
     * @param dynamicModifiers  预收集的动态修饰符（attrId → 修饰符列表），可为空 Map
     * @return 属性快照
     */
    static AttributeSnapshot buildWithModifiers(LivingEntity entity,
                                                IAttributeRegistry registry,
                                                EntityAttributeAttachment attachment,
                                                Map<Identifier, List<IAttributeModifier>> dynamicModifiers) {
        Map<Identifier, AttributeSnapshot.AttributeData> data = new LinkedHashMap<>();

        for (IAttributeEntry entry : registry.getAllEntries()) {
            Identifier attrId = entry.getId();

            // 基础值：附件固有值优先，无则使用注册默认值
            int baseValue = attachment.hasIntrinsicBase(attrId)
                    ? attachment.getIntrinsicBase(attrId)
                    : entry.getDefaultValue();

            // 合并修饰符：持久修饰符 + 动态修饰符
            List<IAttributeModifier> allModifiers = new ArrayList<>();

            // 持久修饰符（装备、光环等已绑定的修饰符）
            List<IAttributeModifier> persistentMods = attachment.getPersistentModifiers(attrId);
            if (!persistentMods.isEmpty()) {
                allModifiers.addAll(persistentMods);
            }

            // 动态修饰符（来自 GatherAttributeEvent 或 GatherAttributeBatchEvent）
            List<IAttributeModifier> dynamicMods = dynamicModifiers.get(attrId);
            if (dynamicMods != null && !dynamicMods.isEmpty()) {
                allModifiers.addAll(dynamicMods);
            }

            // 通过管线计算最终值
            int computedValue = AttributePipeline.compute(attrId, baseValue, allModifiers);

            // 构建 AttributeData
            // 非玩家实体的 maxValue 与 currentValue 相同（上限即计算值）
            data.put(attrId, new AttributeSnapshot.AttributeData(
                    computedValue, computedValue, entry.getDisplayName()
            ));
        }

        return new AttributeSnapshot(data);
    }
}
