package com.rpgcraft.profession;

import com.rpgcraft.core.profession.api.AbstractProfession;
import com.rpgcraft.core.profession.api.IProfession;
import com.rpgcraft.core.profession.api.IProfessionRegistry;
import com.rpgcraft.core.profession.api.ProfessionIds;
import com.rpgcraft.core.profession.api.IProfession.ProfessionType;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ProfessionAvailability} 级联可用性计算的单元测试。
 * <p>
 * 通过 {@link ProfessionAvailability#isEffectiveEnabled(IProfession, IProfessionRegistry)} 实例版入口
 * 直接验证级联逻辑，不依赖 {@link ProfessionManager} 的静态注册表。显式开关通过直接写入
 * {@link ProfessionLoadConfig} 的包私有 {@code explicitMap} 字段模拟（与真实 reload apply 等价）。
 * <p>
 * 不依赖 Minecraft 服务器运行时 —— {@link Identifier}/{@link AbstractProfession} 是纯数据类。
 */
class ProfessionAvailabilityTest {

    /** 构造一个最小可用职业（仅 id + 单前置），无加成。 */
    private static IProfession prof(Identifier id, Identifier prerequisite) {
        return new AbstractProfession(id, id.getPath(), "", ProfessionType.PRIMARY, prerequisite, 20,
                Map.of(), Map.of()) {
        };
    }

    /** 构造一个复合职业（多前置），覆写 getPrerequisites()。 */
    private static IProfession compound(Identifier id, Set<Identifier> prerequisites) {
        return new AbstractProfession(id, id.getPath(), "", ProfessionType.COMPOUND, null, 20,
                Map.of(), Map.of()) {
            @Override
            public Set<Identifier> getPrerequisites() {
                return prerequisites;
            }
        };
    }

    /** 内存注册表，按 ID 注册查询。 */
    private static IProfessionRegistry registryOf(Map<Identifier, IProfession> profs) {
        return new IProfessionRegistry() {
            @Override
            public void register(IProfession profession) {
                profs.put(profession.getId(), profession);
            }

            @Override
            public IProfession getProfession(Identifier id) {
                return profs.get(id);
            }

            @Override
            public java.util.Collection<IProfession> getAllProfessions() {
                return profs.values();
            }

            @Override
            public IProfession getDefault() {
                return profs.get(ProfessionIds.COMMONER_ID);
            }
        };
    }

    private static final Identifier COMMONER = ProfessionIds.COMMONER_ID;
    private static final Identifier WARRIOR = Identifier.fromNamespaceAndPath("rpgcraftcore", "warrior");
    private static final Identifier BERSERKER = Identifier.fromNamespaceAndPath("rpgcraftcore", "berserker");
    private static final Identifier MAGE = Identifier.fromNamespaceAndPath("rpgcraftcore", "mage");
    private static final Identifier WITCHBLADE = Identifier.fromNamespaceAndPath("rpgcraftcore", "witchblade");

    @AfterEach
    void resetConfig() {
        // 恢复显式开关为「空映射」(= 全部默认启用)，避免污染其它测试
        ProfessionLoadConfig.explicitMap = new HashMap<>();
        ProfessionAvailability.invalidateCache();
    }

    private void setExplicit(Identifier id, boolean enabled) {
        // 直接写入包私有 explicitMap，模拟 reload 后的 apply 状态
        Map<Identifier, Boolean> map = new HashMap<>(ProfessionLoadConfig.explicitMap);
        map.put(id, enabled);
        ProfessionLoadConfig.explicitMap = map;
        ProfessionAvailability.invalidateCache();
    }

    @Test
    void allEnabledByDefault() {
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(COMMONER, prof(COMMONER, null));
        profs.put(WARRIOR, prof(WARRIOR, COMMONER));
        profs.put(BERSERKER, prof(BERSERKER, WARRIOR));
        IProfessionRegistry reg = registryOf(profs);

        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(COMMONER), reg));
        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(WARRIOR), reg));
        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(BERSERKER), reg));
    }

    @Test
    void disablingPrerequisiteCascadesToSuccessor() {
        // 链：commoner → warrior → berserker。关闭 warrior 应使 berserker 也被关闭，commoner 仍开。
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(COMMONER, prof(COMMONER, null));
        profs.put(WARRIOR, prof(WARRIOR, COMMONER));
        profs.put(BERSERKER, prof(BERSERKER, WARRIOR));
        IProfessionRegistry reg = registryOf(profs);

        setExplicit(WARRIOR, false);

        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(COMMONER), reg));
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(WARRIOR), reg));
        // 前置被关 → 后继级联关闭（即使 berserker 本身未显式列出 = 默认启用）
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(BERSERKER), reg));
    }

    @Test
    void disablingIntermediateStillExplicitlyEnabledSuccessorAlsoCascades() {
        // 即便 berserker 显式标 true，只要 warrior 被关，berserker 仍被级联关闭
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(COMMONER, prof(COMMONER, null));
        profs.put(WARRIOR, prof(WARRIOR, COMMONER));
        profs.put(BERSERKER, prof(BERSERKER, WARRIOR));
        IProfessionRegistry reg = registryOf(profs);

        setExplicit(WARRIOR, false);
        setExplicit(BERSERKER, true);

        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(COMMONER), reg));
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(WARRIOR), reg));
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(BERSERKER), reg));
    }

    @Test
    void compoundRequiresAllPrerequisitesEnabled() {
        // 复合职业 witchblade 前置 = [berserker, mage]。关 mage → witchblade 被关
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(COMMONER, prof(COMMONER, null));
        profs.put(WARRIOR, prof(WARRIOR, COMMONER));
        profs.put(BERSERKER, prof(BERSERKER, WARRIOR));
        profs.put(MAGE, prof(MAGE, COMMONER));
        profs.put(WITCHBLADE, compound(WITCHBLADE, Set.of(BERSERKER, MAGE)));
        IProfessionRegistry reg = registryOf(profs);

        setExplicit(MAGE, false);

        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(BERSERKER), reg));
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(MAGE), reg));
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(WITCHBLADE), reg));
    }

    @Test
    void missingPrerequisiteInstanceDisablesProfession() {
        // berserker 的前置 warrior 未注册 —— 视为禁用（防御错误配置）
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(COMMONER, prof(COMMONER, null));
        profs.put(BERSERKER, prof(BERSERKER, WARRIOR)); // warrior 不在注册表
        IProfessionRegistry reg = registryOf(profs);

        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(BERSERKER), reg));
    }

    @Test
    void cacheInvalidationReflectsNewConfig() {
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(COMMONER, prof(COMMONER, null));
        profs.put(WARRIOR, prof(WARRIOR, COMMONER));
        IProfessionRegistry reg = registryOf(profs);

        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(WARRIOR), reg));
        setExplicit(WARRIOR, false);
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(WARRIOR), reg));
        // 恢复后再校验
        setExplicit(WARRIOR, true);
        assertTrue(ProfessionAvailability.isEffectiveEnabled(profs.get(WARRIOR), reg));
    }

    @Test
    void cycleInPrerequisitesIsDetectedAndDisabled() {
        // 构造一个前置环：A → B → A（异常配置）。应被环检测按禁用处理，不死循环
        Identifier a = Identifier.fromNamespaceAndPath("rpgcraftcore", "cycle_a");
        Identifier b = Identifier.fromNamespaceAndPath("rpgcraftcore", "cycle_b");
        Map<Identifier, IProfession> profs = new LinkedHashMap<>();
        profs.put(a, prof(a, b));
        profs.put(b, prof(b, a));
        IProfessionRegistry reg = registryOf(profs);

        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(a), reg));
        assertFalse(ProfessionAvailability.isEffectiveEnabled(profs.get(b), reg));
    }
}
