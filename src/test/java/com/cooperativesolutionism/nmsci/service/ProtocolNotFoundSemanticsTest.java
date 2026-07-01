package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtocolNotFoundSemanticsTest {

    @Test
    void centralPubkeyLockLookupByPubkeyReturnsNotFoundWhenMissing() {
        byte[] pubkey = TestKeyPairs.CENTRAL.pubkey();
        CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
        when(repository.findByCentralPubkey(pubkey)).thenReturn(null);
        CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService(
                null,
                repository,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(NotFoundException.class, () -> service.getCentralPubkeyLockedMsgByCentralPubkey(pubkey));
    }

    @Test
    void flowNodeLockLookupByPubkeyReturnsNotFoundWhenMissing() {
        byte[] pubkey = TestKeyPairs.FLOW_NODE_A.pubkey();
        FlowNodeLockedMsgRepository repository = mock(FlowNodeLockedMsgRepository.class);
        when(repository.findByFlowNodePubkey(pubkey)).thenReturn(null);
        FlowNodeLockedMsgService service = new FlowNodeLockedMsgService(
                repository,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(NotFoundException.class, () -> service.getFlowNodeLockedMsgByFlowNodePubkey(pubkey));
    }

    @Test
    void malformedPubkeyStillReturnsBadRequestType() {
        CentralPubkeyLockedMsgService centralPubkeyLockedMsgService = new CentralPubkeyLockedMsgService(
                null, null, null, null, null, null, null, null, null
        );
        FlowNodeLockedMsgService flowNodeLockedMsgService = new FlowNodeLockedMsgService(
                null, null, null, null, null, null, null, null
        );

        assertThrows(IllegalArgumentException.class, () -> centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgByCentralPubkey(new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> flowNodeLockedMsgService.getFlowNodeLockedMsgByFlowNodePubkey(new byte[32]));
    }
}
