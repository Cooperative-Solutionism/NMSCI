package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.annotation.Nonnull;

public interface ConsumeChainService {

    void saveConsumeChain(@Nonnull TransactionMountMsg transactionMountMsg, @Nonnull TransactionRecordMsg transactionRecordMsg);

    ReturningFlowRateResponseDTO getReturningFlowRate(@Nonnull ReturningFlowRateRequestDTO returningFlowRateRequestDTO);

}
