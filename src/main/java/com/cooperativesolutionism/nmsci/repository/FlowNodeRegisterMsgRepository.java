package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FlowNodeRegisterMsgRepository extends JpaRepository<FlowNodeRegisterMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);
}
