package com.cooperativesolutionism.nmsci.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 契约：消息实体采用基于持久化标识(id)的 equals/hashCode，避免默认引用相等导致的
 * 代理-实体不一致问题（参见 LoopMarker 成环判断）。
 */
class MessageEntityIdentityTest {

    private static final List<Supplier<Message>> FACTORIES = List.of(
            TransactionRecordMsg::new,
            TransactionMountMsg::new,
            FlowNodeRegisterMsg::new,
            FlowNodeLockedMsg::new,
            CentralPubkeyEmpowerMsg::new,
            CentralPubkeyLockedMsg::new
    );

    @Test
    void messageEntitiesUseIdBasedEquality() {
        for (Supplier<Message> factory : FACTORIES) {
            UUID id = UUID.randomUUID();

            Message a = factory.get();
            Message b = factory.get();
            a.setId(id);
            b.setId(id);
            String type = a.getClass().getSimpleName();

            assertEquals(a, b, () -> type + " 相同 id 应相等");
            assertEquals(a.hashCode(), b.hashCode(), () -> type + " 相等对象 hashCode 应一致");

            Message other = factory.get();
            other.setId(UUID.randomUUID());
            assertNotEquals(a, other, () -> type + " 不同 id 不应相等");

            Message transient1 = factory.get();
            Message transient2 = factory.get();
            assertNotEquals(transient1, transient2, () -> type + " 两个 id 为 null 的瞬时实例不应相等");
            assertEquals(transient1, transient1, () -> type + " 同一引用应相等");
        }
    }

    @Test
    void differentMessageTypesWithSameIdAreNotEqual() {
        UUID id = UUID.randomUUID();
        TransactionRecordMsg record = new TransactionRecordMsg();
        FlowNodeRegisterMsg register = new FlowNodeRegisterMsg();
        record.setId(id);
        register.setId(id);

        assertNotEquals(record, register, "不同消息类型即使 id 相同也不应相等");
        assertNotEquals(register, record, "equals 应保持对称");
    }
}
