package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 中心公钥公证写入的判重语义（#1 Option A 公钥轮换）：判重按 (流转节点公钥, 中心公钥) 组合，
 * 而非仅按流转节点公钥——这是支持「中心公钥冻结/轮换后重新授权」的写入侧前提，与 V3 迁移的
 * 组合唯一约束、ChainStateReplayer 的逐对回放共同闭环。
 */
class CentralPubkeyEmpowerMsgServiceTest {

    private final CentralPubkeyEmpowerMsgRepository repository = mock(CentralPubkeyEmpowerMsgRepository.class);
    private final MessageWritePipeline messageWritePipeline = mock(MessageWritePipeline.class);
    private final FlowNodeStateValidator flowNodeStateValidator = mock(FlowNodeStateValidator.class);
    private final CentralPubkeyValidator centralPubkeyValidator = mock(CentralPubkeyValidator.class);
    private final SignatureValidator signatureValidator = mock(SignatureValidator.class);
    private final ProtocolRawBytesBuilder protocolRawBytesBuilder = mock(ProtocolRawBytesBuilder.class);
    private final CentralSignatureService centralSignatureService = mock(CentralSignatureService.class);

    private final CentralPubkeyEmpowerMsgService service = new CentralPubkeyEmpowerMsgService(
            repository,
            messageWritePipeline,
            flowNodeStateValidator,
            centralPubkeyValidator,
            signatureValidator,
            protocolRawBytesBuilder,
            centralSignatureService
    );

    @Test
    void rejectsDuplicateAuthorizationForSameFlowNodeAndCentralPair() {
        CentralPubkeyEmpowerMsg msg = empowerMsg(pk((byte) 0x02), pk((byte) 0x03));
        when(repository.countByFlowNodePubkeyAndCentralPubkey(any(), any())).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.saveCentralPubkeyEmpowerMsg(msg));
    }

    @Test
    void duplicateCheckKeysOnFlowNodeAndCentralPairNotFlowNodeAlone() {
        byte[] flow = pk((byte) 0x02);
        byte[] central = pk((byte) 0x03);
        CentralPubkeyEmpowerMsg msg = empowerMsg(flow, central);
        when(repository.countByFlowNodePubkeyAndCentralPubkey(any(), any())).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.saveCentralPubkeyEmpowerMsg(msg));

        // 判重必须按 (flow, central) 组合，且不得再退回仅按 flowNodePubkey 判重（否则会阻断轮换重授权）。
        verify(repository).countByFlowNodePubkeyAndCentralPubkey(aryEq(flow), aryEq(central));
        verify(repository, never()).existsByFlowNodePubkey(any());
    }

    private static CentralPubkeyEmpowerMsg empowerMsg(byte[] flow, byte[] central) {
        CentralPubkeyEmpowerMsg msg = new CentralPubkeyEmpowerMsg();
        msg.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        msg.setFlowNodePubkey(flow);
        msg.setCentralPubkey(central);
        return msg;
    }

    private static byte[] pk(byte prefix) {
        byte[] pubkey = new byte[33];
        Arrays.fill(pubkey, (byte) 0x11);
        pubkey[0] = prefix;
        return pubkey;
    }
}
