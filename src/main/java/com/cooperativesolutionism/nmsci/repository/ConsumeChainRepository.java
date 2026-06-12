package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ConsumeChainRepository extends JpaRepository<ConsumeChain, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ConsumeChain> findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(@NotNull FlowNodeRegisterMsg end, @NotNull Short currencyType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ConsumeChain> findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(
            @NotNull FlowNodeRegisterMsg end,
            @NotNull Short currencyType,
            Pageable pageable
    );

    @Query("select c from ConsumeChain c where c.id in (select e.chain.id from ConsumeChainEdge e where e.relatedTransactionMount = :relatedTransactionMount)")
    Slice<ConsumeChain> findDistinctByRelatedTransactionMount(TransactionMountMsg relatedTransactionMount, Pageable pageable);

    Slice<ConsumeChain> findByStart(FlowNodeRegisterMsg start, Pageable pageable);

    Slice<ConsumeChain> findByStartAndIsLoop(FlowNodeRegisterMsg start, Boolean isLoop, Pageable pageable);

    Slice<ConsumeChain> findByEnd(FlowNodeRegisterMsg end, Pageable pageable);

    Slice<ConsumeChain> findByEndAndIsLoop(FlowNodeRegisterMsg end, Boolean isLoop, Pageable pageable);
}
