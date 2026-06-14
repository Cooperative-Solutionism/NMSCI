package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.dto.LockedMessageResponseDTO;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/central-pubkey-locked-msg")
public class CentralPubkeyLockedMsgController {

    @Resource
    private CentralPubkeyLockedMsgService centralPubkeyLockedMsgService;

    @Resource
    private CentralPubkeyLockedMsgConverter centralPubkeyLockedMsgConverter;

    @PostMapping
    public ResponseResult<CentralPubkeyLockedMsg> saveCentralPubkeyLockedMsg(@RequestBody @ByteArraySize(115) byte[] byteData) {
        CentralPubkeyLockedMsg centralPubkeyLockedMsg = centralPubkeyLockedMsgConverter.fromByteArray(byteData);
        centralPubkeyLockedMsgService.saveCentralPubkeyLockedMsg(centralPubkeyLockedMsg);
        return ResponseResult.success(centralPubkeyLockedMsg);
    }

    @GetMapping("/{id}")
    public ResponseResult<CentralPubkeyLockedMsg> getCentralPubkeyLockedMsgById(@PathVariable String id) {
        return ResponseResult.success(centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgById(UUID.fromString(id)));
    }

    @GetMapping("/central-pubkey/{centralPubkey}")
    public ResponseResult<LockedMessageResponseDTO<CentralPubkeyLockedMsg>> getCentralPubkeyLockedMsgByCentralPubkey(@PathVariable String centralPubkey) {
        Optional<CentralPubkeyLockedMsg> centralPubkeyLockedMsg = centralPubkeyLockedMsgService.findCentralPubkeyLockedMsgByCentralPubkey(ByteArrayUtil.hexToBytes(centralPubkey));
        return ResponseResult.success(new LockedMessageResponseDTO<>(centralPubkeyLockedMsg.isPresent(), centralPubkeyLockedMsg.orElse(null)));
    }
}
