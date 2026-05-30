package com.rpgcraft.core.attribute;

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

        DefaultEntry(Identifier id, String displayName,
                     Supplier<AttachmentType<EntityAttribute>> supplier,
                     int defaultValue, int defaultMaxValue) {
            this.id = id;
            this.displayName = displayName;
            this.supplier = supplier;
            this.defaultValue = defaultValue;
            this.defaultMaxValue = defaultMaxValue;
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

    @Override
    public void register(Identifier id, String displayName, int defaultValue, int defaultMaxValue) {
        Supplier<AttachmentType<EntityAttribute>> supplier = deferredRegister.register(
                id.getPath(),
                () -> AttachmentType.builder(() -> new EntityAttribute(defaultValue, defaultMaxValue))
                        .serialize(EntityAttribute.CODEC)
                        .build()
        );
        entries.put(id, new DefaultEntry(id, displayName, supplier, defaultValue, defaultMaxValue));
        cachedList = null;
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
    public IAttribute getAttribute(LivingEntity entity, Identifier id) {
        AttachmentType<? extends IAttribute> type = getTypeById(id);
        return type != null ? entity.getData(type) : null;
    }
}
