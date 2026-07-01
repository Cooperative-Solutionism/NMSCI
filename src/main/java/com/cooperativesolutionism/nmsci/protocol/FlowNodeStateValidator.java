package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeState;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeStateValidator {

    private final FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;
    private final CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;
    private final FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;

    public FlowNodeStateValidator(
            FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository,
            CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository,
            FlowNodeLockedMsgRepository flowNodeLockedMsgRepository
    ) {
        this.flowNodeRegisterMsgRepository = flowNodeRegisterMsgRepository;
        this.centralPubkeyEmpowerMsgRepository = centralPubkeyEmpowerMsgRepository;
        this.flowNodeLockedMsgRepository = flowNodeLockedMsgRepository;
    }

    public void validateRegistered(byte[] flowNodePubkey) {
        String flowNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(flowNodePubkey);
        if (!flowNodeRegisterMsgRepository.existsByFlowNodePubkey(flowNodePubkey)) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未注册");
        }
    }

    public void validateAuthorized(byte[] flowNodePubkey, byte[] centralPubkey) {
        String flowNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(flowNodePubkey);
        long centralPubkeyEmpowerMsgCount = centralPubkeyEmpowerMsgRepository.countByFlowNodePubkeyAndCentralPubkey(
                flowNodePubkey,
                centralPubkey
        );
        if (centralPubkeyEmpowerMsgCount == 0) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未授权");
        }
    }

    public void validateNotLocked(byte[] flowNodePubkey) {
        String flowNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(flowNodePubkey);
        if (flowNodeLockedMsgRepository.existsByFlowNodePubkey(flowNodePubkey)) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")已冻结");
        }
    }

    public void validateRegisteredAuthorizedAndNotLocked(byte[] flowNodePubkey, byte[] centralPubkey) {
        String flowNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(flowNodePubkey);
        FlowNodeState flowNodeState = flowNodeRegisterMsgRepository.findFlowNodeState(flowNodePubkey, centralPubkey);
        if (flowNodeState == null || !flowNodeState.getRegistered()) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未注册");
        }

        if (!flowNodeState.getAuthorized()) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")未授权");
        }

        if (flowNodeState.getLocked()) {
            throw new IllegalArgumentException("该流转节点公钥(" + flowNodePubkeyBase64 + ")已冻结");
        }
    }
}
