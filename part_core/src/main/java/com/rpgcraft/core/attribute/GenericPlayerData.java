package com.rpgcraft.core.attribute;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class GenericPlayerData {
    // 1. 创建附件类型的延迟注册器，绑定到 MODID
    public static final DeferredRegister<AttachmentType<?>> ATTRIBUTE_ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

    // ====================================================================
    // 2. 显式声明 Identifier 常量
    // 在 NeoForge 26.1 中，ResourceLocation 改为了 Identifier。
    // 为了避免底层注册表 API 变动导致的找不到方法问题，我们直接手动声明 ID，
    // 这样在发送网络包时可以直接引用，无需通过 Supplier/Holder 反向查找。
    // ====================================================================
    public static final Identifier LIFE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "life");
    public static final Identifier SKILL_POINT_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");
    public static final Identifier MAGIC_POINT_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "magic_point");
    public static final Identifier STRENGTH_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    public static final Identifier MANA_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "mana");
    public static final Identifier AGILE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    public static final Identifier PRECISION_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    public static final Identifier DEFENSE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense");
    public static final Identifier RESISTANCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    public static final Identifier CRITICAL_RATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    public static final Identifier CRITICAL_RATIO_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");

    // ====================================================================
    // 3. 注册 AttachmentType
    // 使用 Supplier 包装，并绑定刚才声明的 Codec。
    // 注册名必须与上面的 Identifier 路径(如 "life")保持完全一致！
    // ====================================================================
    public static final Supplier<AttachmentType<PlayerAttribute>> LIFE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "life", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> SKILL_POINT = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "skill_point", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> MAGIC_POINT = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "magic_point", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> STRENGTH = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "strength", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> MANA = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "mana", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> AGILE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "agile", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> PRECISION = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "precision", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> DEFENSE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "defense", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> RESISTANCE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "resistance", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(2, 100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> CRITICAL_RATE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "critical_rate", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(5, 100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    public static final Supplier<AttachmentType<PlayerAttribute>> CRITICAL_RATIO = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "critical_ratio", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(50, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    // ====================================================================
    // 4. 辅助查找方法
    // 客户端收到网络包后，里面只有 Identifier，需要通过此方法找到对应的 AttachmentType，
    // 然后才能从客户端 Player 身上读取/修改数据。
    // ====================================================================
    public static AttachmentType<PlayerAttribute> getTypeById(Identifier id) {
        if (LIFE_ID.equals(id)) return LIFE.get();
        if (SKILL_POINT_ID.equals(id)) return SKILL_POINT.get();
        if (MAGIC_POINT_ID.equals(id)) return MAGIC_POINT.get();
        if (STRENGTH_ID.equals(id)) return STRENGTH.get();
        if (MANA_ID.equals(id)) return MANA.get();
        if (AGILE_ID.equals(id)) return AGILE.get();
        if (PRECISION_ID.equals(id)) return PRECISION.get();
        if (DEFENSE_ID.equals(id)) return DEFENSE.get();
        if (RESISTANCE_ID.equals(id)) return RESISTANCE.get();
        if (CRITICAL_RATE_ID.equals(id)) return CRITICAL_RATE.get();
        if (CRITICAL_RATIO_ID.equals(id)) return CRITICAL_RATIO.get();
        return null;
    }
}
