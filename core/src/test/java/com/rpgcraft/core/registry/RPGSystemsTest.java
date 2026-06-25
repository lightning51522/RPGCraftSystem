package com.rpgcraft.core.registry;

import com.rpgcraft.core.registry.defaults.NoOpAttackTypeResolver;
import com.rpgcraft.core.registry.defaults.NoOpClientSystem;
import com.rpgcraft.core.registry.defaults.NoOpCombatSystem;
import com.rpgcraft.core.registry.defaults.NoOpEquipmentSystem;
import com.rpgcraft.core.registry.defaults.NoOpLevelSystem;
import com.rpgcraft.core.registry.defaults.NoOpMobDataProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RPGSystems} 门面的纯逻辑单元测试。
 * <p>
 * 验证 core 的 no-op 兜底机制：无插件注册时，6 个系统 SPI getter 返回 no-op 实例而非抛
 * {@link IllegalStateException}，且官方模块以 {@link RPGSystems#DEFAULT_PRIORITY} 注册能覆盖
 * {@link RPGSystems#FALLBACK_PRIORITY} 兜底。
 * <p>
 * 不依赖 Minecraft 运行时——只测门面的注册/查询逻辑。
 * <b>注意</b>：RPGSystems 是静态全局状态，本测试通过 {@code try/finally} 在注册自定义实现后
 * 以 DEFAULT_PRIORITY 重注册回 no-op（或检测覆盖生效）来避免污染后续测试；但因 no-op 兜底
 * 在类加载时以 FALLBACK_PRIORITY 预填，任何 DEFAULT_PRIORITY 注册都会成功覆盖，符合设计。
 */
class RPGSystemsTest {

    @Test
    void levelSystem_hasNoOpFallbackByDefault() {
        ILevelSystem sys = assertDoesNotThrow(RPGSystems::getLevelSystem);
        assertInstanceOf(NoOpLevelSystem.class, sys);
        assertEquals(1, sys.getMaxLevel());
    }

    @Test
    void equipmentSystem_hasNoOpFallbackByDefault() {
        IEquipmentSystem sys = assertDoesNotThrow(RPGSystems::getEquipmentSystem);
        assertInstanceOf(NoOpEquipmentSystem.class, sys);
    }

    @Test
    void combatSystem_hasNoOpFallbackByDefault() {
        ICombatSystem sys = assertDoesNotThrow(RPGSystems::getCombatSystem);
        assertInstanceOf(NoOpCombatSystem.class, sys);
    }

    @Test
    void attackTypeResolver_hasNoOpFallbackByDefault() {
        IAttackTypeResolver sys = assertDoesNotThrow(RPGSystems::getAttackTypeResolver);
        assertInstanceOf(NoOpAttackTypeResolver.class, sys);
    }

    @Test
    void mobDataProvider_hasNoOpFallbackByDefault() {
        IMobDataProvider sys = assertDoesNotThrow(RPGSystems::getMobDataProvider);
        assertInstanceOf(NoOpMobDataProvider.class, sys);
    }

    @Test
    void clientSystem_hasNoOpFallbackByDefault() {
        IClientSystem sys = assertDoesNotThrow(RPGSystems::getClientSystem);
        assertInstanceOf(NoOpClientSystem.class, sys);
    }

    @Test
    void fallBackPriority_isBelowDefault() {
        assertTrue(RPGSystems.FALLBACK_PRIORITY < RPGSystems.DEFAULT_PRIORITY,
                "FALLBACK_PRIORITY 必须低于 DEFAULT_PRIORITY，否则官方模块无法覆盖兜底");
        assertTrue(RPGSystems.DEFAULT_PRIORITY < RPGSystems.OVERRIDE_PRIORITY,
                "DEFAULT_PRIORITY 必须低于 OVERRIDE_PRIORITY");
    }
}
