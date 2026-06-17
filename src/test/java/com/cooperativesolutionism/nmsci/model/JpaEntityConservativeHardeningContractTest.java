package com.cooperativesolutionism.nmsci.model;

import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaEntityConservativeHardeningContractTest {

    @Test
    void byteArrayPrimaryKeysRemainUnchangedInThisConservativePass() throws Exception {
        Field blockInfoId = BlockInfo.class.getDeclaredField("id");
        Field msgAbstractId = MsgAbstract.class.getDeclaredField("id");

        assertEquals(byte[].class, blockInfoId.getType());
        assertEquals(byte[].class, msgAbstractId.getType());
        assertTrue(blockInfoId.isAnnotationPresent(Id.class));
        assertTrue(msgAbstractId.isAnnotationPresent(Id.class));
    }

    @Test
    void entitiesDoNotIntroduceVersionFieldsInThisConservativePass() {
        assertFalse(hasVersionField(BlockInfo.class));
        assertFalse(hasVersionField(MsgAbstract.class));
        assertFalse(hasVersionField(ConsumeChain.class));
        assertFalse(hasVersionField(FlowNodeRegisterMsg.class));
    }

    @Test
    void entitiesDoNotOverrideEqualsOrHashCodeInThisConservativePass() {
        assertFalse(overridesObjectMethod(BlockInfo.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(BlockInfo.class, "hashCode"));
        assertFalse(overridesObjectMethod(MsgAbstract.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(MsgAbstract.class, "hashCode"));
        assertFalse(overridesObjectMethod(ConsumeChain.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(ConsumeChain.class, "hashCode"));
        assertFalse(overridesObjectMethod(FlowNodeRegisterMsg.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(FlowNodeRegisterMsg.class, "hashCode"));
    }

    private static boolean hasVersionField(Class<?> entityType) {
        return Arrays.stream(entityType.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(Version.class));
    }

    private static boolean overridesObjectMethod(Class<?> entityType, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = entityType.getDeclaredMethod(methodName, parameterTypes);
            return method.getDeclaringClass().equals(entityType);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
