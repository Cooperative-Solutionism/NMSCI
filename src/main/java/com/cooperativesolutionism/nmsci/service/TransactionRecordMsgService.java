package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

public interface TransactionRecordMsgService {

    TransactionRecordMsg saveTransactionRecordMsg(@Valid @Nonnull TransactionRecordMsg transactionRecordMsg);

}
