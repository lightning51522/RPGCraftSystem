package com.rpgcraft.professions;

import com.mojang.logging.LogUtils;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionRegistry;
import com.rpgcraft.profession.ProfessionManager;
import com.rpgcraft.professions.archer.ArcherProfession;
import com.rpgcraft.professions.archmage.ArchmageProfession;
import com.rpgcraft.professions.berserker.BerserkerProfession;
import com.rpgcraft.professions.commoner.CommonerProfession;
import com.rpgcraft.professions.witchblade.WitchbladeProfession;
import com.rpgcraft.professions.mage.MageProfession;
import com.rpgcraft.professions.marksman.MarksmanProfession;
import com.rpgcraft.professions.naturalist.NaturalistProfession;
import com.rpgcraft.professions.researcher.ResearcherProfession;
import com.rpgcraft.professions.scholar.ScholarProfession;
import com.rpgcraft.professions.sorcerer.SorcererProfession;
import com.rpgcraft.professions.warrior.WarriorProfession;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import org.slf4j.Logger;

/**
 * RPG Professions 模块入口 —— 注册所有内置职业到职业注册中心
 * <p>
 * 本模块（{@code rpgcraftprofessions}）是 RPGCraftSystem 职业内容的具体定义。
 * 所有内置职业由本模块的 Java 类描述，在 {@code @Mod} 构造函数中通过
 * {@link ProfessionManager#getRegistry()} 注册到 {@code rpgcraftprofession} 模块维护的
 * 职业注册中心。
 * <p>
 * <b>加载顺序</b>：通过 {@code neoforge.mods.toml} 声明 {@code rpgcraftprofession} 为
 * {@code AFTER} 依赖，保证 {@code ProfessionManager.init()} 在本模块构造函数之前执行，
 * 因此注册中心此时一定已就绪。
 * <p>
 * 第三方扩展职业：实现自己的 {@code @Mod} 模块，依赖 {@code rpgcraftprofession}，
 * 在构造函数中调用 {@code ProfessionManager.getRegistry().register(new MyProfession())}。
 */
@Mod(ProfessionsMod.MODID)
public class ProfessionsMod {
    public static final String MODID = "rpgcraftprofessions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProfessionsMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("RPG Professions 模块初始化：注册内置职业");
        IProfessionRegistry registry = ProfessionManager.getRegistry();
        if (registry == null) {
            LOGGER.error("职业注册中心未就绪！请确认 rpgcraftprofession 模块已加载且其构造函数先于本模块执行");
            return;
        }
        // 主职业树
        register(registry, new CommonerProfession());
        register(registry, new WarriorProfession());
        register(registry, new ArcherProfession());
        register(registry, new BerserkerProfession());
        register(registry, new MarksmanProfession());
        // 主职业树（魔法系列）：术士 → 法师 → 大法师
        register(registry, new SorcererProfession());
        register(registry, new MageProfession());
        register(registry, new ArchmageProfession());
        // 复合职业：要求多个主职业达满级作为前置，单独成树
        register(registry, new WitchbladeProfession());
        // 副职业树：学者 → 研究员 → 博物学家
        register(registry, new ScholarProfession());
        register(registry, new ResearcherProfession());
        register(registry, new NaturalistProfession());
        // apprentice 占位副职业：仅在无任何真实副职业时使用。当前已有学者系列，
        // 故不再注册；保留类供未来无副职业场景或调试使用。
        // register(registry, new ApprenticeProfession());
        LOGGER.info("已注册 {} 个内置职业", registry.getAllProfessions().size());
    }

    private static void register(IProfessionRegistry registry, IProfession prof) {
        registry.register(prof);
        LOGGER.debug("已注册职业: {} ({})", prof.getId(), prof.getDisplayName());
    }
}
