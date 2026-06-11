package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.persistence.LockModeType;
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

    List<TransactionRecordMsg> findByConsumeNodePubkey(byte[] consumeNodePubkey);

    List<TransactionRecordMsg> findByFlowNodePubkey(byte[] flowNodePubkey);

    List<TransactionRecordMsg> findByConsumeNodePubkeyAndFlowNodePubkey(byte[] consumeNodePubkey, byte[] flowNodePubkey);

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);
}
