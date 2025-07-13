package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionRecordMsgRepository extends JpaRepository<TransactionRecordMsg, UUID> {

    List<TransactionRecordMsg> findByConsumeNodePubkey(byte[] consumeNodePubkey);

    List<TransactionRecordMsg> findByFlowNodePubkey(byte[] flowNodePubkey);

    List<TransactionRecordMsg> findByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey);
}
