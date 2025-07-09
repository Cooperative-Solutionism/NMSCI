package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

public interface CentralPubkeyLockedMsgService {

    CentralPubkeyLockedMsg saveCentralPubkeyLockedMsg(@Valid @Nonnull CentralPubkeyLockedMsg centralPubkeyLockedMsg);

}
