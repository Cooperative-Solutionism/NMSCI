package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping("/id/{id}")
    public ResponseResult<FlowNodeLockedMsg> getFlowNodeLockedMsgById(@PathVariable("id") String id) {
        FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgService.getFlowNodeLockedMsgById(UUID.fromString(id));
        return ResponseResult.success(flowNodeLockedMsg);
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<FlowNodeLockedMsg> getFlowNodeLockedMsgByFlowNodePubkey(@PathVariable("flowNodePubkey") String flowNodePubkey) {
        FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgService.getFlowNodeLockedMsgByFlowNodePubkey(ByteArrayUtil.hexToBytes(flowNodePubkey));
        return ResponseResult.success(flowNodeLockedMsg);
    }
}
