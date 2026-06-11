package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowNodeStateValidatorTest {

    private final FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
    private final CentralPubkeyEmpowerMsgRepository empowerRepository = mock(CentralPubkeyEmpowerMsgRepository.class);
    private final FlowNodeLockedMsgRepository lockedRepository = mock(FlowNodeLockedMsgRepository.class);
    private final FlowNodeStateValidator validator = new FlowNodeStateValidator(registerRepository, empowerRepository, lockedRepository);

    @Test
    void rejectsUnregisteredFlowNode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())
        );

        assertEquals("该流转节点公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")未注册", exception.getMessage());
    }

    @Test
    void rejectsUnauthorizedFlowNode() {
        when(registerRepository.existsByFlowNodePubkey(TestKeyPairs.FLOW_NODE_A.pubkey())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())
        );

        assertEquals("该流转节点公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")未授权", exception.getMessage());
    }

    @Test
    void rejectsLockedFlowNode() {
        when(registerRepository.existsByFlowNodePubkey(TestKeyPairs.FLOW_NODE_A.pubkey())).thenReturn(true);
        when(empowerRepository.countByFlowNodePubkeyAndCentralPubkey(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())).thenReturn(1L);
        when(lockedRepository.existsByFlowNodePubkey(TestKeyPairs.FLOW_NODE_A.pubkey())).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())
        );

        assertEquals("该流转节点公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")已冻结", exception.getMessage());
    }
}
