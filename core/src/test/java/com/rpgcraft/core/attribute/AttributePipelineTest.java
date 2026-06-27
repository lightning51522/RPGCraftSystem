package com.rpgcraft.core.attribute;

import com.rpgcraft.core.attribute.api.IAttributeModifier;
import com.rpgcraft.core.attribute.api.Operation;
import com.rpgcraft.core.event.RPGEventBus;
import com.rpgcraft.core.event.attribute.AttributeFinalizeEvent;
import com.rpgcraft.core.event.attribute.AttributePostAdditionEvent;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link AttributePipeline#compute} 的纯逻辑单元测试。
 * <p>
 * 验证属性管线的核心数学语义与阶段顺序（ADDITION → MULTIPLY_BASE → MULTIPLY_TOTAL → 截断），
 * 以及事件插手点（{@link AttributePostAdditionEvent} / {@link AttributeFinalizeEvent}）。
 * <p>
 * 不依赖 Minecraft 运行时——只测纯计算。事件总线在无监听器时 {@code post()} 立即返回，
 * 故基础用例无需注册任何监听器。
 */
class AttributePipelineTest {

    private static final Identifier ATTR_ID = Identifier.fromNamespaceAndPath("test", "attr");

    @AfterEach
    void cleanupEventBus() {
        // 测试可能注册临时的 finalize/post-addition 监听器，逐个清理避免污染其他用例
        RPGEventBus.unregisterAll(AttributeFinalizeEvent.class);
        RPGEventBus.unregisterAll(AttributePostAdditionEvent.class);
    }

    @Test
    void emptyModifiers_returnsBaseClampedToZero() {
        List<IAttributeModifier> empty = List.of();
        assertEquals(10, AttributePipeline.compute(ATTR_ID, 10, empty));
        assertEquals(0, AttributePipeline.compute(ATTR_ID, -5, empty),
                "负基础值应被截断为 0");
    }

    @Test
    void additionModifiers_summedOntoBase() {
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(
                AttributeModifier.of(src("a"), Operation.ADDITION, 5),
                AttributeModifier.of(src("b"), Operation.ADDITION, 15)
        );
        assertEquals(30, AttributePipeline.compute(ATTR_ID, 10, mods));
    }

    @Test
    void additionPushingNegative_isClampedToZero() {
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(AttributeModifier.of(src("a"), Operation.ADDITION, -50));
        assertEquals(0, AttributePipeline.compute(ATTR_ID, 10, mods));
    }

    @Test
    void multiplyBase_appliesToPostAdditionValue() {
        // base 10 + ADDITION 10 = 20；MULTIPLY_BASE 50 → 20 * 1.5 = 30
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(
                AttributeModifier.of(src("add"), Operation.ADDITION, 10),
                AttributeModifier.of(src("mb"), Operation.MULTIPLY_BASE, 50)
        );
        assertEquals(30, AttributePipeline.compute(ATTR_ID, 10, mods));
    }

    @Test
    void multiplyTotal_appliesAfterMultiplyBase() {
        // base 10 + ADDITION 10 = 20；MULTIPLY_BASE 100 → 40；MULTIPLY_TOTAL 50 → 60
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(
                AttributeModifier.of(src("add"), Operation.ADDITION, 10),
                AttributeModifier.of(src("mb"), Operation.MULTIPLY_BASE, 100),
                AttributeModifier.of(src("mt"), Operation.MULTIPLY_TOTAL, 50)
        );
        assertEquals(60, AttributePipeline.compute(ATTR_ID, 10, mods));
    }

    @Test
    void multipleMultiplyBase_sumPercentagesBeforeApplying() {
        // base 100；MULTIPLY_BASE 20 + 30 → 100 * 1.5 = 150（百分比先求和再乘一次，非复利）
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(
                AttributeModifier.of(src("mb1"), Operation.MULTIPLY_BASE, 20),
                AttributeModifier.of(src("mb2"), Operation.MULTIPLY_BASE, 30)
        );
        assertEquals(150, AttributePipeline.compute(ATTR_ID, 100, mods));
    }

    @Test
    void postAdditionEvent_canInterceptBeforeMultiplication() {
        // 监听 post-addition：把加算结果改写为 100，再经 MULTIPLY_BASE 100 → 200
        RPGEventBus.register(AttributePostAdditionEvent.class, e -> {
            if (e.getAttributeId().equals(ATTR_ID)) {
                e.setValue(100);
            }
        });
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(
                AttributeModifier.of(src("add"), Operation.ADDITION, 10),
                AttributeModifier.of(src("mb"), Operation.MULTIPLY_BASE, 100)
        );
        assertEquals(200, AttributePipeline.compute(ATTR_ID, 10, mods));
    }

    @Test
    void finalizeEvent_canOverrideResultBeforeClamp() {
        // 监听 finalize：强制覆盖为 999
        RPGEventBus.register(AttributeFinalizeEvent.class, e -> {
            if (e.getAttributeId().equals(ATTR_ID)) {
                e.setValue(999);
            }
        });
        List<IAttributeModifier> mods = List.<IAttributeModifier>of(AttributeModifier.of(src("add"), Operation.ADDITION, 10));
        assertEquals(999, AttributePipeline.compute(ATTR_ID, 10, mods));
    }

    private static Identifier src(String name) {
        return Identifier.fromNamespaceAndPath("test", name);
    }
}
