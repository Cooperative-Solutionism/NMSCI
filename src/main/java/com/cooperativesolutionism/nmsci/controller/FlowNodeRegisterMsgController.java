package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flow-node-register-msg")
public class FlowNodeRegisterMsgController {

    @Resource
    private FlowNodeRegisterMsgService flowNodeRegisterMsgMsgService;

    @PostMapping("/send")
    public ResponseResult<FlowNodeRegisterMsg> saveFlowNodeRegisterMsg(@RequestBody @ByteArraySize(156) byte[] byteData) {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = FlowNodeRegisterMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeRegisterMsgMsgService.saveFlowNodeRegisterMsg(flowNodeRegisterMsgMsg));
    }
}