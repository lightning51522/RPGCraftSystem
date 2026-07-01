package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import com.rpgcraft.core.ui.IAttributeRendererFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

/**
 * {@link IAttributeRegistry} 的默认实现
 * <p>
 * 管理所有 RPG 属性的注册、查询和遍历。
 * 通过 {@link DeferredRegister} 将属性注册为 NeoForge AttachmentType。
 */
public class DefaultAttributeRegistry implements IAttributeRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultAttributeRegistry.class);

    private final DeferredRegister<AttachmentType<?>> deferredRegister;
    private final Map<Identifier, DefaultEntry> entries = new LinkedHashMap<>();
    private final List<IAttributeEntry> respawnResetEntries = new ArrayList<>();
    private List<IAttributeEntry> cachedList = null;

    /**
     * 属性条目的默认实现
     */
	    public static class DefaultEntry implements IAttributeEntry {
	        private final Identifier id;
	        private final String displayName;
	        private final String description;
	        private final Supplier<AttachmentType<EntityAttribute>> supplier;
	        private final int defaultValue;
	        private final int defaultMaxValue;
	        private final boolean resetOnRespawn;
	        private final boolean equipmentAffectsMax;
	        private final boolean allocatable;
	        private final boolean availableAsAffix;
	        private final IAttributeRendererFactory rendererFactory;

	        DefaultEntry(Identifier id, String displayName, String description,
	                     Supplier<AttachmentType<EntityAttribute>> supplier,
	                     int defaultValue, int defaultMaxValue, boolean resetOnRespawn,
	                     boolean equipmentAffectsMax, IAttributeRendererFactory rendererFactory) {
	            this(id, displayName, description, supplier, defaultValue, defaultMaxValue,
	                    resetOnRespawn, equipmentAffectsMax, !resetOnRespawn, rendererFactory);
	        }

	        DefaultEntry(Identifier id, String displayName, String description,
	                     Supplier<AttachmentType<EntityAttribute>> supplier,
	                     int defaultValue, int defaultMaxValue, boolean resetOnRespawn,
	                     boolean equipmentAffectsMax, boolean allocatable,
	                     IAttributeRendererFactory rendererFactory) {
	            this(id, displayName, description, supplier, defaultValue, defaultMaxValue,
	                    resetOnRespawn, equipmentAffectsMax, allocatable, false, rendererFactory);
	        }

	        DefaultEntry(Identifier id, String displayName, String description,
	                     Supplier<AttachmentType<EntityAttribute>> supplier,
	                     int defaultValue, int defaultMaxValue, boolean resetOnRespawn,
	                     boolean equipmentAffectsMax, boolean allocatable, boolean availableAsAffix,
	                     IAttributeRendererFactory rendererFactory) {
	            this.id = id;
	            this.displayName = displayName;
	            this.description = description;
	            this.supplier = supplier;
	            this.defaultValue = defaultValue;
	            this.defaultMaxValue = defaultMaxValue;
	            this.resetOnRespawn = resetOnRespawn;
	            this.equipmentAffectsMax = equipmentAffectsMax;
	            this.allocatable = allocatable;
	            this.availableAsAffix = availableAsAffix;
	            this.rendererFactory = rendererFactory;
	        }

        @Override
        public Identifier getId() { return id; }

        @Override
        public String getDisplayName() { return displayName; }

        @Override
        @SuppressWarnings("unchecked")
        public Supplier<AttachmentType<IAttribute>> getSupplier() {
            return (Supplier<AttachmentType<IAttribute>>) (Supplier<?>) supplier;
        }

        @Override
        public int getDefaultValue() { return defaultValue; }

        @Override
        public int getDefaultMaxValue() { return defaultMaxValue; }

        @Override
        public boolean isCapped() { return defaultMaxValue < Integer.MAX_VALUE; }

        @Override
        public boolean shouldResetOnRespawn() { return resetOnRespawn; }

        @Override
        public boolean equipmentAffectsMax() { return equipmentAffectsMax; }

        @Override
        public String getDescription() { return description; }

        @Override
        public IAttributeRendererFactory getRendererFactory() { return rendererFactory; }

        @Override
        public boolean isAllocatable() { return allocatable; }

        @Override
        public boolean isAvailableAsAffix() { return availableAsAffix; }
    }

    public DefaultAttributeRegistry(String modId) {
        this.deferredRegister = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, modId);
    }

    /**
     * 获取底层 DeferredRegister，用于注册到 Mod 事件总线
     */
    public DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    /**
     * 获取属性条目的内部 Supplier 引用（跳过 Map 查找）
     * <p>
     * 用于 {@link AttributeManager} 的 Supplier 字段直接引用，
     * 消除每次 {@code .get()} 调用时的 HashMap 查找和类型转换开销。
     */
    Supplier<AttachmentType<EntityAttribute>> getRawSupplier(Identifier id) {
        DefaultEntry entry = entries.get(id);
        return entry != null ? entry.supplier : null;
    }

    @Override
    public void register(Identifier id, String displayName, String description,
                         int defaultValue, int defaultMaxValue,
                         boolean resetOnRespawn, boolean equipmentAffectsMax) {
        register(id, displayName, description, defaultValue, defaultMaxValue,
                resetOnRespawn, equipmentAffectsMax, null);
    }

    /**
     * 注册属性（完整参数，含说明文字和自定义渲染器工厂）
     * <p>
     * 此方法为注册链的最终调用点，所有其他 {@code register()} 重载最终委托到此方法。
     *
     * @param id               属性网络标识符
     * @param displayName      属性显示名称
     * @param description      属性说明文字（角色界面悬停 tooltip，空字符串表示无说明）
     * @param defaultValue     默认值
     * @param defaultMaxValue  默认上限值
     * @param resetOnRespawn   重生时是否恢复
     * @param equipmentAffectsMax 装备加成是否影响上限
     * @param rendererFactory  自定义渲染器工厂（可为 null，使用默认文本渲染）
     */
    public void register(Identifier id, String displayName, String description,
                         int defaultValue, int defaultMaxValue,
                         boolean resetOnRespawn, boolean equipmentAffectsMax,
                         IAttributeRendererFactory rendererFactory) {
        register(id, displayName, description, defaultValue, defaultMaxValue,
                resetOnRespawn, equipmentAffectsMax, !resetOnRespawn, rendererFactory);
    }

    /**
     * 注册属性（完整参数 + allocatable 控制）。
     *
     * @param allocatable 是否允许玩家分配属性点到此属性；
     *                    默认应等于 {@code !resetOnRespawn}（资源型不可加点）
     */
    public void register(Identifier id, String displayName, String description,
                         int defaultValue, int defaultMaxValue,
                         boolean resetOnRespawn, boolean equipmentAffectsMax,
                         boolean allocatable, IAttributeRendererFactory rendererFactory) {
        register(id, displayName, description, defaultValue, defaultMaxValue,
                resetOnRespawn, equipmentAffectsMax, allocatable, false, rendererFactory);
    }

    /**
     * 注册属性（完整参数 + allocatable 控制 + 词条可用性标志）—— 注册链的最终调用点。
     * <p>
     * 与上一重载的区别仅在末尾追加 {@code availableAsAffix}：声明此属性是否可作为宝石镶嵌词条出现
     * （详见 {@link IAttributeEntry#isAvailableAsAffix()}）。供需要把自己暴露给宝石系统的属性注册方使用
     * （如 {@code rpgcraftattributes} 的默认属性集、第三方属性模块）。
     *
     * @param availableAsAffix 是否允许此属性作为宝石词条出现；{@code false} 时宝石系统不会枚举到它
     */
    public void register(Identifier id, String displayName, String description,
                         int defaultValue, int defaultMaxValue,
                         boolean resetOnRespawn, boolean equipmentAffectsMax,
                         boolean allocatable, boolean availableAsAffix,
                         IAttributeRendererFactory rendererFactory) {
        boolean capped = defaultMaxValue < Integer.MAX_VALUE;
        Supplier<AttachmentType<EntityAttribute>> supplier = deferredRegister.register(
                id.getPath(),
                () -> AttachmentType.builder(() -> {
                    EntityAttribute attr = new EntityAttribute(id, displayName, defaultValue, defaultMaxValue,
                            capped, resetOnRespawn, equipmentAffectsMax);
                    return attr;
                })
                        .serialize(EntityAttribute.CODEC)
                        .build()
        );
        DefaultEntry entry = new DefaultEntry(id, displayName, description, supplier, defaultValue, defaultMaxValue,
                resetOnRespawn, equipmentAffectsMax, allocatable, availableAsAffix, rendererFactory);
        entries.put(id, entry);
        if (resetOnRespawn) {
            respawnResetEntries.add(entry);
        }
        cachedList = null;
    }

    /**
     * 获取需要在重生时恢复的属性列表
     */
    public List<IAttributeEntry> getRespawnResetEntries() {
        return Collections.unmodifiableList(respawnResetEntries);
    }

    @Override
    public IAttributeEntry getEntry(Identifier id) {
        IAttributeEntry entry = entries.get(id);
        if (entry == null) {
            LOGGER.warn("属性 ID '{}' 在注册表中不存在（所属模组可能已被卸载），返回 null", id);
        }
        return entry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public AttachmentType<IAttribute> getTypeById(Identifier id) {
        DefaultEntry entry = entries.get(id);
        if (entry == null) {
            LOGGER.warn("属性 ID '{}' 在注册表中不存在（所属模组可能已被卸载），返回 null", id);
            return null;
        }
        return (AttachmentType<IAttribute>) (AttachmentType<?>) entry.supplier.get();
    }

    @Override
    public List<IAttributeEntry> getAllEntries() {
        if (cachedList == null) {
            cachedList = List.copyOf(entries.values());
        }
        return cachedList;
    }

    @Override
    public void resetToDefaults(LivingEntity entity) {
        for (IAttributeEntry entry : getAllEntries()) {
            IAttribute attr = entity.getData(entry.getSupplier());
            attr.setMaxValue(entry.getDefaultMaxValue());
            attr.setValue(entry.getDefaultValue());
        }
    }

    @Override
    public IAttribute getAttribute(LivingEntity entity, Identifier id) {
        AttachmentType<? extends IAttribute> type = getTypeById(id);
        return type != null ? entity.getData(type) : null;
    }

    @Override
    public AttributeSnapshot createSnapshot(LivingEntity entity) {
        // 非玩家实体：使用 MobSnapshotBuilder（整合附件数据 + GatherAttributeEvent）
        if (!(entity instanceof Player)) {
            EntityAttributeAttachment attachment = entity.getData(
                    AttributeManager.ENTITY_ATTRIBUTE_ATTACHMENT);
            if (!attachment.isEmpty()) {
                return MobSnapshotBuilder.build(entity, this, attachment);
            }
        }

        // 玩家 或 附件为空的非玩家实体：直接读取 EntityAttribute 附件
        Map<Identifier, AttributeSnapshot.AttributeData> data = new LinkedHashMap<>();
        for (IAttributeEntry entry : getAllEntries()) {
            IAttribute attr = entity.getData(entry.getSupplier());
            data.put(entry.getId(), new AttributeSnapshot.AttributeData(
                    attr.getValue(), attr.getMaxValue(), entry.getDisplayName()));
        }
        return new AttributeSnapshot(data);
    }

    @Override
    public void applySnapshot(LivingEntity entity, AttributeSnapshot snapshot) {
        for (IAttributeEntry entry : getAllEntries()) {
            AttributeSnapshot.AttributeData values = snapshot.get(entry.getId());
            if (values == null) continue;

            IAttribute attr = entity.getData(entry.getSupplier());
            attr.setMaxValue(values.maxValue());
            if (entry.shouldResetOnRespawn()) {
                attr.fillMax();
            } else {
                attr.setValue(values.currentValue());
            }
        }
    }
}
