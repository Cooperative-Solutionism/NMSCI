package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRecordMsgRepository extends JpaRepository<TransactionRecordMsg, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TransactionRecordMsg t where t.id = :id")
    Optional<TransactionRecordMsg> findByIdForUpdate(@Param("id") UUID id);

    Slice<TransactionRecordMsg> findByConsumeNodePubkey(byte[] consumeNodePubkey, Pageable pageable);

    Slice<TransactionRecordMsg> findByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    Slice<TransactionRecordMsg> findByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey, Pageable pageable);

    @Query("""
            select t from TransactionRecordMsg t
            where (:consumeNodePubkey is null or t.consumeNodePubkey = :consumeNodePubkey)
              and (:flowNodePubkey is null or t.flowNodePubkey = :flowNodePubkey)
              and (:currencyType is null or t.currencyType = :currencyType)
              and (:startTime is null or t.confirmTimestamp >= :startTime)
              and (:endTime is null or t.confirmTimestamp <= :endTime)
            """)
    Slice<TransactionRecordMsg> search(
            @Param("consumeNodePubkey") byte[] consumeNodePubkey,
            @Param("flowNodePubkey") byte[] flowNodePubkey,
            @Param("currencyType") Short currencyType,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable
    );

    List<MessagePayloadProjection> findPayloadByIdIn(Collection<UUID> ids);
}
