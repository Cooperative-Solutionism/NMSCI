package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

public interface TransactionMountMsgService {

    TransactionMountMsg saveTransactionMountMsg(@Valid @Nonnull TransactionMountMsg transactionMountMsg);
}
