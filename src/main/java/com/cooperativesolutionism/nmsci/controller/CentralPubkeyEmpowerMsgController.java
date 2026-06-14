package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/central-pubkey-empower-msg")
public class CentralPubkeyEmpowerMsgController {

    @Resource
    private CentralPubkeyEmpowerMsgService centralPubkeyEmpowerMsgService;

    @Resource
    private CentralPubkeyEmpowerMsgConverter centralPubkeyEmpowerMsgConverter;

    @PostMapping
    public ResponseResult<CentralPubkeyEmpowerMsg> saveCentralPubkeyEmpowerMsg(@RequestBody @ByteArraySize(148) byte[] byteData) {
        CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(centralPubkeyEmpowerMsgService.saveCentralPubkeyEmpowerMsg(centralPubkeyEmpowerMsg));
    }

    @GetMapping("/{id}")
    public ResponseResult<CentralPubkeyEmpowerMsg> getCentralPubkeyEmpowerMsgById(@PathVariable("id") String id) {
        CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgService.getCentralPubkeyEmpowerMsgById(UUID.fromString(id));
        return ResponseResult.success(centralPubkeyEmpowerMsg);
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<CentralPubkeyEmpowerMsg> getCentralPubkeyEmpowerMsgByFlowNodePubkey(@PathVariable("flowNodePubkey") String flowNodePubkey) {
        CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgService.getCentralPubkeyEmpowerMsgByFlowNodePubkey(ByteArrayUtil.hexToBytes(flowNodePubkey));
        return ResponseResult.success(centralPubkeyEmpowerMsg);
    }
}
