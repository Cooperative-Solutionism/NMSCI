package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsumeChainRepository extends JpaRepository<ConsumeChain, UUID> {

    List<ConsumeChain> findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc(@NotNull FlowNodeRegisterMsg end, @NotNull Short currencyType);

}
