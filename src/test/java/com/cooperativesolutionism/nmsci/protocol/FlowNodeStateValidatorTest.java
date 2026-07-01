package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeState;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowNodeStateValidatorTest {

    private final FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
    private final CentralPubkeyEmpowerMsgRepository empowerRepository = mock(CentralPubkeyEmpowerMsgRepository.class);
    private final FlowNodeLockedMsgRepository lockedRepository = mock(FlowNodeLockedMsgRepository.class);
    private final FlowNodeStateValidator validator = new FlowNodeStateValidator(registerRepository, empowerRepository, lockedRepository);

    @Test
    void validatesCombinedStateWithOneAggregateRepositoryCall() {
        when(registerRepository.findFlowNodeState(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey()))
                .thenReturn(new TestFlowNodeState(true, true, false));

        validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey());

        verify(registerRepository).findFlowNodeState(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey());
        verifyNoInteractions(empowerRepository, lockedRepository);
    }

    @Test
    void rejectsUnregisteredFlowNode() {
        when(registerRepository.findFlowNodeState(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey()))
                .thenReturn(new TestFlowNodeState(false, false, false));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())
        );

        assertEquals("该流转节点公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")未注册", exception.getMessage());
        verifyNoInteractions(empowerRepository, lockedRepository);
    }

    @Test
    void rejectsUnauthorizedFlowNode() {
        when(registerRepository.findFlowNodeState(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey()))
                .thenReturn(new TestFlowNodeState(true, false, true));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())
        );

        assertEquals("该流转节点公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")未授权", exception.getMessage());
        verifyNoInteractions(empowerRepository, lockedRepository);
    }

    @Test
    void rejectsLockedFlowNode() {
        when(registerRepository.findFlowNodeState(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey()))
                .thenReturn(new TestFlowNodeState(true, true, true));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateRegisteredAuthorizedAndNotLocked(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.CENTRAL.pubkey())
        );

        assertEquals("该流转节点公钥(" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64() + ")已冻结", exception.getMessage());
        verifyNoInteractions(empowerRepository, lockedRepository);
    }

    private record TestFlowNodeState(boolean registered, boolean authorized, boolean locked) implements FlowNodeState {

        @Override
        public boolean getRegistered() {
            return registered;
        }

        @Override
        public boolean getAuthorized() {
            return authorized;
        }

        @Override
        public boolean getLocked() {
            return locked;
        }
    }
}
