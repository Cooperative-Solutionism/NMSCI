package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;

@RestController
@RequestMapping("/central-pubkey-empowerments")
public class CentralPubkeyEmpowerMsgController {

    @Resource
    private CentralPubkeyEmpowerMsgService centralPubkeyEmpowerMsgService;

    @Resource
    private CentralPubkeyEmpowerMsgConverter centralPubkeyEmpowerMsgConverter;

    @PostMapping
    public ResponseResult<CentralPubkeyEmpowerMsg> saveCentralPubkeyEmpowerMsg(@RequestBody @ByteArraySize(CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES) byte[] byteData) {
        CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(centralPubkeyEmpowerMsgService.saveCentralPubkeyEmpowerMsg(centralPubkeyEmpowerMsg));
    }

    @GetMapping("/{id}")
    public ResponseResult<CentralPubkeyEmpowerMsg> getCentralPubkeyEmpowerMsgById(@PathVariable("id") String id) {
        CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgService.getCentralPubkeyEmpowerMsgById(uuid(id));
        return ResponseResult.success(centralPubkeyEmpowerMsg);
    }

    @GetMapping
    public ResponseResult<SliceResponseDTO<CentralPubkeyEmpowerMsg>> listCentralPubkeyEmpowerMsgs(
            @RequestParam(required = false) String flowNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<CentralPubkeyEmpowerMsg> centralPubkeyEmpowerMsgs = centralPubkeyEmpowerMsgService.listCentralPubkeyEmpowerMsgs(
                hexBytesOrNull(flowNodePubkey),
                PageRequestUtil.ofMessageQuery(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(centralPubkeyEmpowerMsgs));
    }
}
