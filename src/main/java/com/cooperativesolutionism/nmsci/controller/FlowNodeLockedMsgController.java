package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flow-node-locked-msg")
public class FlowNodeLockedMsgController {
    @Resource
    private FlowNodeLockedMsgService flowNodeLockedMsgService;

    @PostMapping("/send")
    public ResponseResult<FlowNodeLockedMsg> saveFlowNodeLockedMsg(@RequestBody @ByteArraySize(148) byte[] byteData) {
        FlowNodeLockedMsg flowNodeLockedMsg = FlowNodeLockedMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeLockedMsgService.saveFlowNodeLockedMsg(flowNodeLockedMsg));
    }
}
