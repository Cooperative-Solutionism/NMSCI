package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

public interface FlowNodeRegisterMsgService {

    FlowNodeRegisterMsg saveFlowNodeRegisterMsg(@Valid @Nonnull FlowNodeRegisterMsg flowNodeRegisterMsg);

}
