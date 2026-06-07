package com.rpgcraft.core.profession;

import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionProvider;
import com.rpgcraft.core.attribute.AttributeManager;
import com.rpgcraft.core.attribute.EntityAttribute;
import com.rpgcraft.core.attribute.api.IAttributeEntry;
import com.rpgcraft.core.network.SyncPlayerAttributePacket;
import com.rpgcraft.core.network.SyncPlayerProfessionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Supplier;

/**
 * 职业模块全局门面
 * <p>
 * 保留对 {@link ProfessionRegistry} 和 {@link ProfessionData} 附件的静态引用，
 * 遵循 {@link com.rpgcraft.core.level.LevelManager} 的门面模式。
 * <p>
 * 职业提供属性加成（有正有负），切换职业时自动移除旧加成、应用新加成。
 */
public class ProfessionManager {

    private static ProfessionRegistry registry;
    private static DeferredRegister<AttachmentType<?>> deferredRegister;

    // ====================================================================
    // 职业标识符常量
    // ====================================================================

    public static final Identifier COMMONER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "commoner");
    public static final Identifier WARRIOR_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "warrior");
    public static final Identifier ARCHER_ID =
            Identifier.fromNamespaceAndPath("rpgcraftcore", "archer");

    // ====================================================================
    // AttachmentType Supplier
    // ====================================================================

    /** ProfessionData 附件 Supplier */
    public static Supplier<AttachmentType<ProfessionData>> PLAYER_PROFESSION;

    /**
     * 初始化职业模块
     * <p>
     * 创建注册中心、注册附件类型、注册内置职业，
     * 并通过 SPI 发现子模组提供的自定义职业。
     * 必须在 {@link #getDeferredRegister().register(modEventBus)} 之前调用。
     */
    public static void init() {
        registry = new ProfessionRegistry();

        deferredRegister = DeferredRegister.create(
                NeoForgeRegistries.ATTACHMENT_TYPES, "rpgcraftcore");

        PLAYER_PROFESSION = deferredRegister.register("player_profession",
                () -> AttachmentType.builder(ProfessionData::new)
                        .serialize(ProfessionData.CODEC)
                        .build()
        );

        // 注册内置职业
        registry.register(new CommonerProfession());
        registry.register(new WarriorProfession());
        registry.register(new ArcherProfession());

        // SPI：发现子模组注册的自定义职业
        for (IProfessionProvider provider : ServiceLoader.load(IProfessionProvider.class)) {
            provider.registerProfessions(registry);
        }
    }

    public static ProfessionRegistry getRegistry() {
        return registry;
    }

    public static DeferredRegister<AttachmentType<?>> getDeferredRegister() {
        return deferredRegister;
    }

    // ====================================================================
    // 查询方法
    // ====================================================================

    /**
     * 获取玩家的当前主职业
     *
     * @param player 服务端玩家
     * @return 当前职业实例
     */
    public static IProfession getProfession(ServerPlayer player) {
        ProfessionData data = player.getData(PLAYER_PROFESSION);
        return registry.getProfession(data.getProfessionId());
    }

    // ====================================================================
    // 职业切换
    // ====================================================================

    /**
     * 设置玩家的主职业
     * <p>
     * 流程：移除旧职业加成 → 应用新职业加成 → 更新附件数据 → 同步到客户端。
     *
     * @param player        服务端玩家
     * @param professionId  新职业标识符
     */
    public static void setProfession(ServerPlayer player, Identifier professionId) {
        IProfession newProfession = registry.getProfession(professionId);
        if (newProfession == null) return;

        IProfession oldProfession = getProfession(player);

        // 1. 移除旧职业加成
        applyBonus(player, oldProfession.getBonusMap(), false);

        // 2. 应用新职业加成
        applyBonus(player, newProfession.getBonusMap(), true);

        // 3. 更新附件数据
        ProfessionData data = player.getData(PLAYER_PROFESSION);
        data.setProfessionId(professionId);

        // 4. 同步职业和受影响的属性到客户端
        syncToClient(player);
        syncAffectedAttributes(player, oldProfession, newProfession);
    }

    // ====================================================================
    // 加成应用
    // ====================================================================

    /**
     * 应用当前职业的属性加成到玩家
     * <p>
     * 用于首次设置职业或需要重新应用加成的场景。
     *
     * @param player 服务端玩家
     */
    public static void applyProfessionBonuses(ServerPlayer player) {
        IProfession prof = getProfession(player);
        applyBonus(player, prof.getBonusMap(), true);
    }

    /**
     * 移除当前职业的属性加成
     * <p>
     * 用于切换职业前移除旧加成。
     *
     * @param player 服务端玩家
     */
    public static void removeProfessionBonuses(ServerPlayer player) {
        IProfession prof = getProfession(player);
        applyBonus(player, prof.getBonusMap(), false);
    }

    /**
     * 内部方法：应用或移除一组属性加成
     *
     * @param player   服务端玩家
     * @param bonusMap 属性加成映射
     * @param add      {@code true} 为加，{@code false} 为减
     */
    private static void applyBonus(ServerPlayer player, Map<Identifier, Integer> bonusMap, boolean add) {
        int sign = add ? 1 : -1;
        for (Map.Entry<Identifier, Integer> entry : bonusMap.entrySet()) {
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(entry.getKey());
            if (attrEntry == null) continue;

            EntityAttribute attr = (EntityAttribute) player.getData(attrEntry.getSupplier());
            int delta = sign * entry.getValue();
            attr.setValue(Math.clamp(attr.getValue() + delta, 0, attr.getMaxValue()));
        }
    }

    // ====================================================================
    // 网络同步
    // ====================================================================

    /**
     * 同步玩家职业数据到客户端
     *
     * @param player 服务端玩家
     */
    public static void syncToClient(ServerPlayer player) {
        ProfessionData data = player.getData(PLAYER_PROFESSION);
        SyncPlayerProfessionPacket.sendToClient(player, data);
    }

    /**
     * 同步受职业切换影响的属性到客户端
     */
    private static void syncAffectedAttributes(ServerPlayer player,
                                                IProfession oldProf,
                                                IProfession newProf) {
        // 收集所有涉及的属性 ID
        java.util.Set<Identifier> affected = new java.util.HashSet<>();
        affected.addAll(oldProf.getBonusMap().keySet());
        affected.addAll(newProf.getBonusMap().keySet());

        for (Identifier attrId : affected) {
            IAttributeEntry attrEntry = AttributeManager.getRegistry().getEntry(attrId);
            if (attrEntry == null) continue;
            EntityAttribute attr = (EntityAttribute) player.getData(attrEntry.getSupplier());
            SyncPlayerAttributePacket.sendToClient(player, attrId, attr);
        }
    }
}
