package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CentralPubkeyValidatorTest {

    private final CentralPubkeyLockedMsgRepository lockedRepository = mock(CentralPubkeyLockedMsgRepository.class);
    private final CentralPubkeyValidator validator = new CentralPubkeyValidator(properties(), lockedRepository);

    @Test
    void exposesCurrentCentralPubkey() {
        assertArrayEquals(TestKeyPairs.CENTRAL.pubkey(), validator.currentCentralPubkey());
    }

    @Test
    void rejectsLockedCentralPubkeyBeforeCheckingCurrentPubkey() {
        when(lockedRepository.existsByCentralPubkey(TestKeyPairs.FLOW_NODE_A.pubkey())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateCurrentAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey())
        );

        assertEquals("该中心公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")已被冻结", exception.getMessage());
    }

    @Test
    void rejectsNonCurrentCentralPubkey() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateCurrentAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey())
        );

        assertEquals("中心公钥设置错误，当前中心公钥为:(" + TestKeyPairs.CENTRAL.pubkeyBase64() + ")", exception.getMessage());
    }

    private NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }
}
