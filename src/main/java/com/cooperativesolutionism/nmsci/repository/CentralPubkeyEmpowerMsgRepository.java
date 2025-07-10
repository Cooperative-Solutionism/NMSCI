package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CentralPubkeyEmpowerMsgRepository extends JpaRepository<CentralPubkeyEmpowerMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);

    long countByFlowNodePubkeyAndCentralPubkey(byte[] flowNodePubkey, byte[] centralPubkey);
}
