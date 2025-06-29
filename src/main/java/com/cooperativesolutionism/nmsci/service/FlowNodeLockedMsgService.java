package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

public interface FlowNodeLockedMsgService {

    FlowNodeLockedMsg saveFlowNodeLockedMsg(@Valid @Nonnull FlowNodeLockedMsg flowNodeLockedMsg);
}
