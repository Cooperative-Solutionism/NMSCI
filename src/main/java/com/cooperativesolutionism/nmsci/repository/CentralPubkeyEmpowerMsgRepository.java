package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CentralPubkeyEmpowerMsgRepository extends JpaRepository<CentralPubkeyEmpowerMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);

    long countByFlowNodePubkeyAndCentralPubkey(byte[] flowNodePubkey, byte[] centralPubkey);

    CentralPubkeyEmpowerMsg findByFlowNodePubkey(byte[] flowNodePubkey);

    Slice<CentralPubkeyEmpowerMsg> findByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);

}
