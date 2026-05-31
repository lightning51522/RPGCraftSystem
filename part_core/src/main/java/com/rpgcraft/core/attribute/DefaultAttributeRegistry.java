package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.AttributeSnapshot;
import com.rpgcraft.core.attribute.api.IAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.attribute.api.IAttributeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.*;
import java.util.function.Supplier;

/**
 * {@link IAttributeRegistry} 的默认实现
 * <p>
 * 管理所有 RPG 属性的注册、查询和遍历。
 * 通过 {@link DeferredRegister} 将属性注册为 NeoForge AttachmentType。
 */
public class DefaultAttributeRegistry implements IAttributeRegistry {

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
        private final Supplier<AttachmentType<EntityAttribute>> supplier;
        private final int defaultValue;
        private final int defaultMaxValue;
        private final boolean resetOnRespawn;
        private final boolean equipmentAffectsMax;

        DefaultEntry(Identifier id, String displayName,
                     Supplier<AttachmentType<EntityAttribute>> supplier,
                     int defaultValue, int defaultMaxValue, boolean resetOnRespawn, boolean equipmentAffectsMax) {
            this.id = id;
            this.displayName = displayName;
            this.supplier = supplier;
            this.defaultValue = defaultValue;
            this.defaultMaxValue = defaultMaxValue;
            this.resetOnRespawn = resetOnRespawn;
            this.equipmentAffectsMax = equipmentAffectsMax;
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
     * 用于 {@link GenericEntityData} 的 Supplier 字段直接引用，
     * 消除每次 {@code .get()} 调用时的 HashMap 查找和类型转换开销。
     */
    Supplier<AttachmentType<EntityAttribute>> getRawSupplier(Identifier id) {
        DefaultEntry entry = entries.get(id);
        return entry != null ? entry.supplier : null;
    }

    @Override
    public void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue) {
        register(id, displayName, defaultValue, defaultMaxValue, false);
    }

    @Override
    public void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue, boolean resetOnRespawn) {
        register(id, displayName, defaultValue, defaultMaxValue, resetOnRespawn, false);
    }

    @Override
    public void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue, boolean resetOnRespawn, boolean equipmentAffectsMax) {
        Supplier<AttachmentType<EntityAttribute>> supplier = deferredRegister.register(
                id.getPath(),
                () -> AttachmentType.builder(() -> new EntityAttribute(displayName, defaultValue, defaultMaxValue))
                        .serialize(EntityAttribute.CODEC)
                        .build()
        );
        DefaultEntry entry = new DefaultEntry(id, displayName, supplier, defaultValue, defaultMaxValue, resetOnRespawn, equipmentAffectsMax);
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
        return entries.get(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public AttachmentType<IAttribute> getTypeById(Identifier id) {
        DefaultEntry entry = entries.get(id);
        return entry != null ? (AttachmentType<IAttribute>) (AttachmentType<?>) entry.supplier.get() : null;
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
