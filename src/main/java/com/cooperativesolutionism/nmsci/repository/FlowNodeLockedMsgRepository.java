package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FlowNodeLockedMsgRepository extends JpaRepository<FlowNodeLockedMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);

    FlowNodeLockedMsg findByFlowNodePubkey(byte[] flowNodePubkey);

    List<MessagePayloadProjection> findPayloadByIdIn(Collection<UUID> ids);
}
