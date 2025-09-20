package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/flow-node-register-msg")
public class FlowNodeRegisterMsgController {

    @Resource
    private FlowNodeRegisterMsgService flowNodeRegisterMsgMsgService;

    @PostMapping("/send")
    public ResponseResult<FlowNodeRegisterMsg> saveFlowNodeRegisterMsg(@RequestBody @ByteArraySize(123) byte[] byteData) {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = FlowNodeRegisterMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeRegisterMsgMsgService.saveFlowNodeRegisterMsg(flowNodeRegisterMsgMsg));
    }

    @GetMapping("/id/{id}")
    public ResponseResult<FlowNodeRegisterMsg> getFlowNodeRegisterMsgById(@PathVariable("id") String id) {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = flowNodeRegisterMsgMsgService.getFlowNodeRegisterMsgById(UUID.fromString(id));
        return ResponseResult.success(flowNodeRegisterMsgMsg);
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<FlowNodeRegisterMsg> getFlowNodeRegisterMsgByFlowNodePubkey(@PathVariable("flowNodePubkey") String flowNodePubkey) {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = flowNodeRegisterMsgMsgService.getFlowNodeRegisterMsgByFlowNodePubkey(ByteArrayUtil.hexToBytes(flowNodePubkey));
        return ResponseResult.success(flowNodeRegisterMsgMsg);
    }
}