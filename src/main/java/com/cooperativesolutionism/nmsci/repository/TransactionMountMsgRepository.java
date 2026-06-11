package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionMountMsgRepository extends JpaRepository<TransactionMountMsg, UUID> {

    boolean existsTransactionMountMsgByMountedTransactionRecordId(UUID mountedTransactionRecordId);

    Slice<TransactionMountMsg> findByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable);

    Slice<TransactionMountMsg> findByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    Slice<TransactionMountMsg> findByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey, Pageable pageable);

    TransactionMountMsg findByMountedTransactionRecordId(UUID mountedTransactionRecordId);

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);
}
