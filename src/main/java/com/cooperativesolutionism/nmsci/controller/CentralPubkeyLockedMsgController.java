package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/central-pubkey-locked-msg")
public class CentralPubkeyLockedMsgController {

    @Resource
    private CentralPubkeyLockedMsgService centralPubkeyLockedMsgService;

    @PostMapping("/send")
    public void saveCentralPubkeyLockedMsg(@RequestBody @ByteArraySize(115) byte[] byteData) {
        CentralPubkeyLockedMsg centralPubkeyLockedMsg = CentralPubkeyLockedMsgConverter.fromByteArray(byteData);
        centralPubkeyLockedMsgService.saveCentralPubkeyLockedMsg(centralPubkeyLockedMsg);
    }
}
