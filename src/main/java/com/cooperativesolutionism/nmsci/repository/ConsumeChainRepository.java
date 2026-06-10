package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.UUID;

public interface ConsumeChainRepository extends JpaRepository<ConsumeChain, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ConsumeChain> findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(@NotNull FlowNodeRegisterMsg end, @NotNull Short currencyType);

    List<ConsumeChain> findByStart(FlowNodeRegisterMsg start);

    List<ConsumeChain> findByStartAndIsLoop(FlowNodeRegisterMsg start, Boolean isLoop);

    List<ConsumeChain> findByEnd(FlowNodeRegisterMsg end);

    List<ConsumeChain> findByEndAndIsLoop(FlowNodeRegisterMsg end, Boolean isLoop);
}
