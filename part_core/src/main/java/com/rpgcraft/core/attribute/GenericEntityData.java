package com.rpgcraft.core.attribute;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
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
public class GenericEntityData {

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
    // 每个属性注册为一个 AttachmentType<EntityAttribute>。
    // register() 的第一个参数必须与上方 Identifier 的路径字符串完全一致。
    // builder() 中：
    //   - supplier 提供默认值（新玩家/无存档数据时的初始属性）
    //   - serialize() 绑定 EntityAttribute.CODEC 用于存档序列化
    // ====================================================================

    /** 生命值：上限 100，默认满值 */
    public static final Supplier<AttachmentType<EntityAttribute>> LIFE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "life", () -> AttachmentType.builder(() -> new EntityAttribute(100))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 技力值：上限 100，默认满值 */
    public static final Supplier<AttachmentType<EntityAttribute>> SKILL_POINT = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "skill_point", () -> AttachmentType.builder(() -> new EntityAttribute(100))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 法力值：上限 100，默认满值 */
    public static final Supplier<AttachmentType<EntityAttribute>> MAGIC_POINT = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "magic_point", () -> AttachmentType.builder(() -> new EntityAttribute(100))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 力量：无上限，默认 10 */
    public static final Supplier<AttachmentType<EntityAttribute>> STRENGTH = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "strength", () -> AttachmentType.builder(() -> new EntityAttribute(10, Integer.MAX_VALUE))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 魔力：无上限，默认 10 */
    public static final Supplier<AttachmentType<EntityAttribute>> MANA = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "mana", () -> AttachmentType.builder(() -> new EntityAttribute(10, Integer.MAX_VALUE))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 敏捷：无上限，默认 10 */
    public static final Supplier<AttachmentType<EntityAttribute>> AGILE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "agile", () -> AttachmentType.builder(() -> new EntityAttribute(10, Integer.MAX_VALUE))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 精准：无上限，默认 10 */
    public static final Supplier<AttachmentType<EntityAttribute>> PRECISION = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "precision", () -> AttachmentType.builder(() -> new EntityAttribute(10, Integer.MAX_VALUE))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 防御：无上限，默认 10 */
    public static final Supplier<AttachmentType<EntityAttribute>> DEFENSE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "defense", () -> AttachmentType.builder(() -> new EntityAttribute(10, Integer.MAX_VALUE))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 法术抗性：上限 100，默认 2 */
    public static final Supplier<AttachmentType<EntityAttribute>> RESISTANCE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "resistance", () -> AttachmentType.builder(() -> new EntityAttribute(2, 100))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 暴击率：上限 100，默认 5 */
    public static final Supplier<AttachmentType<EntityAttribute>> CRITICAL_RATE = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "critical_rate", () -> AttachmentType.builder(() -> new EntityAttribute(5, 100))
                    .serialize(EntityAttribute.CODEC)
                    .build()
    );

    /** 暴击伤害倍率：无上限，默认 50（表示 50%） */
    public static final Supplier<AttachmentType<EntityAttribute>> CRITICAL_RATIO = ATTRIBUTE_ATTACHMENT_TYPES.register(
            "critical_ratio", () -> AttachmentType.builder(() -> new EntityAttribute(50, Integer.MAX_VALUE))
                    .serialize(EntityAttribute.CODEC)
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
    public static AttachmentType<EntityAttribute> getTypeById(Identifier id) {
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
    public record AttributeEntry(Identifier id, Supplier<AttachmentType<EntityAttribute>> supplier) {
    }

    // ====================================================================
    // 伤害计算
    // ====================================================================

    /**
     * 计算玩家受到伤害后的最终数值
     * <p>
     * 根据伤害类型应用不同的减免规则：
     * <ul>
     *   <li><b>物理伤害 ({@link AttackType#PHYSICAL})：</b>最终伤害 = max(0, 原始伤害 - 防御力)</li>
     *   <li><b>法术伤害 ({@link AttackType#MAGIC})：</b>最终伤害 = 原始伤害 × (1 - 法术抗性%)</li>
     *   <li><b>其他混合类型：</b>暂不处理，返回原始伤害</li>
     * </ul>
     *
     * @param entity         受击实体，用于读取其防御力和法术抗性属性
     * @param originalDamage 原始伤害数值（未被减免前）
     * @param type           伤害类型
     * @return 减免后的最终伤害数值（不低于 0）
     */
    public static int getHurt(LivingEntity entity, int originalDamage, AttackType type) {
        return switch (type) {
            case PHYSICAL -> {
                // 物理伤害：直接减去防御力值，最低为 0
                int defense = entity.getData(DEFENSE).getValue();
                yield Math.max(0, originalDamage - defense);
            }
            case MAGIC -> {
                // 法术伤害：按法术抗性百分比减免
                // resistance 范围 0~100，表示减免百分比（如 resistance=2 → 减免 2%）
                int resistance = entity.getData(RESISTANCE).getValue();
                yield (int) Math.max(0, originalDamage * (1.0 - resistance / 100.0));
            }
            // 混合类型暂不处理，直接返回原始伤害
            default -> originalDamage;
        };
    }

    /**
     * 计算玩家造成的伤害数值
     * <p>
     * 根据攻击类型确定基础伤害，并按暴击率判定是否暴击：
     * <ul>
     *   <li><b>物理伤害 ({@link AttackType#PHYSICAL})：</b>基础伤害 = 力量值</li>
     *   <li><b>法术伤害 ({@link AttackType#MAGIC})：</b>基础伤害 = 魔力值</li>
     *   <li><b>暴击判定：</b>生成 [0, 100) 的随机整数，若小于暴击率则触发暴击</li>
     *   <li><b>暴击加成：</b>最终伤害 = 基础伤害 × (1 + 暴击伤害%)
     *       （暴击伤害默认 50 表示 50%，即暴击时伤害 ×1.5）</li>
     * </ul>
     *
     * @param entity 攻击方实体，用于读取力量/魔力、暴击率和暴击伤害属性
     * @param type   攻击类型（仅 PHYSICAL 和 MAGIC 已实现）
     * @return 计算后的最终伤害数值；若攻击类型未实现则返回 0
     */
    public static int causeDamage(LivingEntity entity, AttackType type) {
        // 根据攻击类型确定基础伤害
        int baseDamage = switch (type) {
            case PHYSICAL -> entity.getData(STRENGTH).getValue();
            case MAGIC -> entity.getData(MANA).getValue();
            // 混合类型暂不处理
            default -> 0;
        };

        // 暴击判定：暴击率范围 0~100，表示暴击概率百分比
        int critRate = entity.getData(CRITICAL_RATE).getValue();
        boolean isCrit = ThreadLocalRandom.current().nextInt(100) < critRate;

        if (isCrit) {
            // 暴击伤害：基础伤害 × (1 + 暴击伤害百分比)
            // critRatio 默认 50 → 50% 加成 → 最终伤害 = base × 1.5
            int critRatio = entity.getData(CRITICAL_RATIO).getValue();
            return (int) (baseDamage * (1.0 + critRatio / 100.0));
        }

        return baseDamage;
    }
}
