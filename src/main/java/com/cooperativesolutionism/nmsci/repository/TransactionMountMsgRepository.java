package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionMountMsgRepository extends JpaRepository<TransactionMountMsg, UUID> {

    boolean existsTransactionMountMsgByMountedTransactionRecordId(UUID mountedTransactionRecordId);

    List<TransactionMountMsg> findByConsumeNodePubkey(byte[] consumeNodePubkey);

    List<TransactionMountMsg> findByFlowNodePubkey(byte[] flowNodePubkey);

    List<TransactionMountMsg> findByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey);

    TransactionMountMsg findByMountedTransactionRecordId(UUID mountedTransactionRecordId);

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);
}
