package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TransactionMountMsgRepository extends JpaRepository<TransactionMountMsg, UUID> {

    boolean existsTransactionMountMsgByMountedTransactionRecordId(UUID mountedTransactionRecordId);

    Slice<TransactionMountMsg> findByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable);

    Slice<TransactionMountMsg> findByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    Slice<TransactionMountMsg> findByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey, Pageable pageable);

    TransactionMountMsg findByMountedTransactionRecordId(UUID mountedTransactionRecordId);

    @Query("""
            select t from TransactionMountMsg t
            where (:consumeNodePubkey is null or t.consumeNodePubkey = :consumeNodePubkey)
              and (:flowNodePubkey is null or t.flowNodePubkey = :flowNodePubkey)
              and (:mountedTransactionRecordId is null or t.mountedTransactionRecordId = :mountedTransactionRecordId)
              and (:startTime is null or t.confirmTimestamp >= :startTime)
              and (:endTime is null or t.confirmTimestamp <= :endTime)
            """)
    Slice<TransactionMountMsg> search(
            @Param("consumeNodePubkey") byte[] consumeNodePubkey,
            @Param("flowNodePubkey") byte[] flowNodePubkey,
            @Param("mountedTransactionRecordId") UUID mountedTransactionRecordId,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable
    );

    List<MessagePayloadProjection> findPayloadByIdIn(Collection<UUID> ids);
}
