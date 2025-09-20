package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.Message;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

public interface MsgAbstractService {

    void saveMsgAbstract(@Valid @Nonnull Message message);

}
