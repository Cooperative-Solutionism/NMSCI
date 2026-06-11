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
    void cachesDecodedCurrentCentralPubkeyAfterConstruction() {
        CountingProperties properties = new CountingProperties();
        CentralPubkeyValidator cachingValidator = new CentralPubkeyValidator(properties, lockedRepository);

        cachingValidator.currentCentralPubkey();
        cachingValidator.currentCentralPubkey();
        cachingValidator.validateCurrent(TestKeyPairs.CENTRAL.pubkey());

        assertEquals(1, properties.getCentralPubkeyBase64ReadCount());
    }

    @Test
    void returnsDefensiveCopyOfCachedCentralPubkey() {
        byte[] currentCentralPubkey = validator.currentCentralPubkey();
        currentCentralPubkey[0] = (byte) (currentCentralPubkey[0] + 1);

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

    private static class CountingProperties extends NmsciProperties {
        private int centralPubkeyBase64ReadCount;

        private CountingProperties() {
            CentralKeyPair centralKeyPair = new CentralKeyPair();
            centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
            centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
            setCentralKeyPair(centralKeyPair);
        }

        @Override
        public String getCentralPubkeyBase64() {
            centralPubkeyBase64ReadCount++;
            return super.getCentralPubkeyBase64();
        }

        private int getCentralPubkeyBase64ReadCount() {
            return centralPubkeyBase64ReadCount;
        }
    }
}
