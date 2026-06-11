package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FlowNodeRegisterMsgRepository extends JpaRepository<FlowNodeRegisterMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);

    FlowNodeRegisterMsg findFirstByFlowNodePubkey(byte[] flowNodePubkey);

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);

}
