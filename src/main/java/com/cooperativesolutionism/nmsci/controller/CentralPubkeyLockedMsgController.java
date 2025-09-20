package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/id/{id}")
    public ResponseResult<CentralPubkeyLockedMsg> getCentralPubkeyLockedMsgById(@PathVariable String id) {
        return ResponseResult.success(centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgById(UUID.fromString(id)));
    }

    @GetMapping("/central-pubkey/{centralPubkey}")
    public ResponseResult<CentralPubkeyLockedMsg> getCentralPubkeyLockedMsgByCentralPubkey(@PathVariable String centralPubkey) {
        return  ResponseResult.success(centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgByCentralPubkey(ByteArrayUtil.hexToBytes(centralPubkey)));
    }
}
