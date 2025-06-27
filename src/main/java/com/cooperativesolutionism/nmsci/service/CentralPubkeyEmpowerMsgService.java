package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import jakarta.validation.Valid;

public interface CentralPubkeyEmpowerMsgService {

    CentralPubkeyEmpowerMsg saveCentralPubkeyEmpowerMsg(@Valid CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg);
}
