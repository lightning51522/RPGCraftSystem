package com.rpgcraft.core.attribute;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.function.Supplier;

/**
 * 全局玩家属性注册中心
 * <p>
 * 集中声明、注册和管理所有自定义 RPG 属性的 {@link AttachmentType}。
 * NeoForge 的 AttachmentType 系统允许将自定义数据附加到游戏对象（如玩家实体）上，
 * 并自动处理存档序列化。
 * <p>
 * <b>架构设计：</b>
 * <ol>
 *   <li>通过 {@link #ATTRIBUTE_ATTACHMENT_TYPES} 延迟注册器将所有属性类型注册到 NeoForge</li>
 *   <li>每个属性都有一个 {@link Identifier} 常量（用于网络包标识）和一个
 *       {@link Supplier Supplier&lt;AttachmentType&gt;} 常量（用于读写数据）</li>
 *   <li>通过 {@link #getTypeById(Identifier)} 实现网络包 ID → AttachmentType 的查找</li>
 *   <li>通过 {@link #ALL_ATTRIBUTES} 列表支持批量遍历（如登录时全量同步）</li>
 * </ol>
 * <p>
 * <b>属性分类：</b>
 * <ul>
 *   <li>有上限属性（maxValue &lt; Integer.MAX_VALUE）：生命、技力、法力、法抗、暴击率</li>
 *   <li>无上限属性（maxValue == Integer.MAX_VALUE）：力量、魔力、敏捷、精准、防御、暴击伤害</li>
 * </ul>
 */
public class GenericPlayerData {

    /**
     * AttachmentType 延迟注册器
     * <p>
     * 在主类构造函数中通过 {@code ATTRIBUTE_ATTACHMENT_TYPES.register(modEventBus)} 注册到 Mod 事件总线。
     * 所有通过此注册器注册的 AttachmentType 都会被 NeoForge 管理，自动支持存档读写。
     */
    public static final DeferredRegister<AttachmentType<?>> ATTRIBUTE_ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

    // ====================================================================
    // Identifier 常量
    // 每个属性的唯一标识符，用于网络包中标识属性类型。
    // 客户端收到网络包后通过这些 ID 查找对应的 AttachmentType。
    // ====================================================================

    /** 生命值标识符 */
    public static final Identifier LIFE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "life");
    /** 技力值标识符 */
    public static final Identifier SKILL_POINT_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "skill_point");
    /** 法力值标识符 */
    public static final Identifier MAGIC_POINT_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "magic_point");
    /** 力量标识符 */
    public static final Identifier STRENGTH_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "strength");
    /** 魔力标识符 */
    public static final Identifier MANA_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "mana");
    /** 敏捷标识符 */
    public static final Identifier AGILE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "agile");
    /** 精准标识符 */
    public static final Identifier PRECISION_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "precision");
    /** 防御标识符 */
    public static final Identifier DEFENSE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "defense");
    /** 法术抗性标识符 */
    public static final Identifier RESISTANCE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "resistance");
    /** 暴击率标识符 */
    public static final Identifier CRITICAL_RATE_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_rate");
    /** 暴击伤害倍率标识符 */
    public static final Identifier CRITICAL_RATIO_ID = Identifier.fromNamespaceAndPath("rpgcraftcore", "critical_ratio");

    // ====================================================================
    // AttachmentType 注册
    // 每个属性注册为一个 AttachmentType<PlayerAttribute>。
    // register() 的第一个参数必须与上方 Identifier 的路径字符串完全一致。
    // builder() 中：
    //   - supplier 提供默认值（新玩家/无存档数据时的初始属性）
    //   - serialize() 绑定 PlayerAttribute.CODEC 用于存档序列化
    // ====================================================================

    /** 生命值：上限 100，默认满值 */
    public static final Supplier<AttachmentType<PlayerAttribute>> LIFE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "life", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 技力值：上限 100，默认满值 */
    public static final Supplier<AttachmentType<PlayerAttribute>> SKILL_POINT = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "skill_point", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 法力值：上限 100，默认满值 */
    public static final Supplier<AttachmentType<PlayerAttribute>> MAGIC_POINT = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "magic_point", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 力量：无上限，默认 10 */
    public static final Supplier<AttachmentType<PlayerAttribute>> STRENGTH = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "strength", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 魔力：无上限，默认 10 */
    public static final Supplier<AttachmentType<PlayerAttribute>> MANA = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "mana", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 敏捷：无上限，默认 10 */
    public static final Supplier<AttachmentType<PlayerAttribute>> AGILE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "agile", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 精准：无上限，默认 10 */
    public static final Supplier<AttachmentType<PlayerAttribute>> PRECISION = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "precision", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 防御：无上限，默认 10 */
    public static final Supplier<AttachmentType<PlayerAttribute>> DEFENSE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "defense", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(10, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 法术抗性：上限 100，默认 2 */
    public static final Supplier<AttachmentType<PlayerAttribute>> RESISTANCE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "resistance", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(2, 100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 暴击率：上限 100，默认 5 */
    public static final Supplier<AttachmentType<PlayerAttribute>> CRITICAL_RATE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "critical_rate", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(5, 100))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    /** 暴击伤害倍率：无上限，默认 50（表示 50%） */
    public static final Supplier<AttachmentType<PlayerAttribute>> CRITICAL_RATIO = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "critical_ratio", () -> AttachmentType.<PlayerAttribute>builder(() -> new PlayerAttribute(50, Integer.MAX_VALUE))
                    .serialize(PlayerAttribute.CODEC)
                    .build()
    );

    // ====================================================================
    // 辅助方法与数据结构
    // ====================================================================

    /**
     * 通过 Identifier 查找对应的 AttachmentType
     * <p>
     * 客户端收到 {@link com.rpgcraft.core.network.SyncPlayerAttributePacket} 后，
     * 网络包中只携带属性的 Identifier，需要通过此方法查找对应的 AttachmentType，
     * 然后才能从客户端玩家实例上读写属性数据。
     *
     * @param id 属性的 Identifier
     * @return 对应的 AttachmentType，若未找到则返回 null
     */
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

    /**
     * 所有属性的 (Identifier, Supplier) 配对列表
     * <p>
     * 用于需要批量遍历所有属性的场景，例如：
     * <ul>
     *   <li>玩家登录时全量同步属性到客户端</li>
     *   <li>调试命令中批量输出所有属性值</li>
     * </ul>
     * 使用不可变 List，避免运行时意外修改。
     */
    public static final List<AttributeEntry> ALL_ATTRIBUTES = List.of(
            new AttributeEntry(LIFE_ID, LIFE),
            new AttributeEntry(SKILL_POINT_ID, SKILL_POINT),
            new AttributeEntry(MAGIC_POINT_ID, MAGIC_POINT),
            new AttributeEntry(STRENGTH_ID, STRENGTH),
            new AttributeEntry(MANA_ID, MANA),
            new AttributeEntry(AGILE_ID, AGILE),
            new AttributeEntry(PRECISION_ID, PRECISION),
            new AttributeEntry(DEFENSE_ID, DEFENSE),
            new AttributeEntry(RESISTANCE_ID, RESISTANCE),
            new AttributeEntry(CRITICAL_RATE_ID, CRITICAL_RATE),
            new AttributeEntry(CRITICAL_RATIO_ID, CRITICAL_RATIO)
    );

    /**
     * 属性条目记录类
     * <p>
     * 将属性的 {@link Identifier}（网络标识）和 {@link Supplier}（数据访问器）绑定在一起，
     * 便于在批量操作中同时获取两者，无需手动配对。
     *
     * @param id       属性的网络标识符
     * @param supplier 属性的 AttachmentType 供应器
     */
    public record AttributeEntry(Identifier id, Supplier<AttachmentType<PlayerAttribute>> supplier) {
    }
}
