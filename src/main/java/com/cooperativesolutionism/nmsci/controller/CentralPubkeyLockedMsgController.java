package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.dto.LockedMessageResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;

@RestController
@RequestMapping("/central-pubkey-locks")
public class CentralPubkeyLockedMsgController {

    @Resource
    private CentralPubkeyLockedMsgService centralPubkeyLockedMsgService;

    @Resource
    private CentralPubkeyLockedMsgConverter centralPubkeyLockedMsgConverter;

    @PostMapping
    public ResponseResult<CentralPubkeyLockedMsg> saveCentralPubkeyLockedMsg(@RequestBody @ByteArraySize(CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES) byte[] byteData) {
        CentralPubkeyLockedMsg centralPubkeyLockedMsg = centralPubkeyLockedMsgConverter.fromByteArray(byteData);
        centralPubkeyLockedMsgService.saveCentralPubkeyLockedMsg(centralPubkeyLockedMsg);
        return ResponseResult.success(centralPubkeyLockedMsg);
    }

    @GetMapping("/{id}")
    public ResponseResult<CentralPubkeyLockedMsg> getCentralPubkeyLockedMsgById(@PathVariable String id) {
        return ResponseResult.success(centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgById(uuid(id)));
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
        Optional<CentralPubkeyLockedMsg> centralPubkeyLockedMsg = centralPubkeyLockedMsgService.findCentralPubkeyLockedMsgByCentralPubkey(hexBytesOrNull(centralPubkey));
        return ResponseResult.success(new LockedMessageResponseDTO<>(centralPubkeyLockedMsg.isPresent(), centralPubkeyLockedMsg.orElse(null)));
    }
}
