package com.cooperativesolutionism.nmsci.consume;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ConsumeChainSupport {

    private final FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;
    private final ConsumeChainEdgeRepository consumeChainEdgeRepository;

    public ConsumeChainSupport(
            FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository,
            ConsumeChainEdgeRepository consumeChainEdgeRepository
    ) {
        this.flowNodeRegisterMsgRepository = flowNodeRegisterMsgRepository;
        this.consumeChainEdgeRepository = consumeChainEdgeRepository;
    }

    public FlowNodeRegisterMsg getFlowNodeRegisterMsgByPubkey(byte[] flowNodePubkey, String roleName) {
        if (flowNodePubkey == null || flowNodePubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException(roleName + "流转节点公钥不能为空或长度不为33字节");
        }

        FlowNodeRegisterMsg flowNodeRegisterMsg = flowNodeRegisterMsgRepository.findFirstByFlowNodePubkey(flowNodePubkey);
        if (flowNodeRegisterMsg == null) {
            throw new NotFoundException(roleName + "流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")不存在");
        }

        return flowNodeRegisterMsg;
    }

    public Map<UUID, List<ConsumeChainEdge>> getEdgesByChainId(List<ConsumeChain> consumeChains) {
        Map<UUID, List<ConsumeChainEdge>> edgesByChainId = new HashMap<>();
        if (consumeChains.isEmpty()) {
            return edgesByChainId;
        }

        List<ConsumeChainEdge> consumeChainEdges = consumeChainEdgeRepository.findByChainInOrderByRelatedTransactionMountTimestampAsc(consumeChains);
        for (ConsumeChainEdge consumeChainEdge : consumeChainEdges) {
            UUID chainId = consumeChainEdge.getChain().getId();
            edgesByChainId.computeIfAbsent(chainId, unused -> new ArrayList<>()).add(consumeChainEdge);
        }

        return edgesByChainId;
    }

}
