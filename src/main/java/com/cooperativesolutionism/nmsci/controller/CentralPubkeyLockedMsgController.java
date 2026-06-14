package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.dto.LockedMessageResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/central-pubkey-locks")
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

    @GetMapping
    public ResponseResult<SliceResponseDTO<CentralPubkeyLockedMsg>> listCentralPubkeyLockedMsgs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<CentralPubkeyLockedMsg> centralPubkeyLockedMsgs = centralPubkeyLockedMsgService.listCentralPubkeyLockedMsgs(
                PageRequestUtil.ofMessageQuery(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(centralPubkeyLockedMsgs));
    }

    @GetMapping("/status")
    public ResponseResult<LockedMessageResponseDTO<CentralPubkeyLockedMsg>> getCentralPubkeyLockStatus(@RequestParam String centralPubkey) {
        Optional<CentralPubkeyLockedMsg> centralPubkeyLockedMsg = centralPubkeyLockedMsgService.findCentralPubkeyLockedMsgByCentralPubkey(hexToBytesOrNull(centralPubkey));
        return ResponseResult.success(new LockedMessageResponseDTO<>(centralPubkeyLockedMsg.isPresent(), centralPubkeyLockedMsg.orElse(null)));
    }

    private static byte[] hexToBytesOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }
}
