package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CentralPubkeyLockedMsgPersistenceService {

    @Resource
    private CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Transactional
    public void save(CentralPubkeyLockedMsg centralPubkeyLockedMsg) {
        centralPubkeyLockedMsgRepository.save(centralPubkeyLockedMsg);
        msgAbstractService.saveMsgAbstract(centralPubkeyLockedMsg);
    }
}
