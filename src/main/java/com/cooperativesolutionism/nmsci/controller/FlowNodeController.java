package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.FlowNodeStateResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/flow-node")
public class FlowNodeController {

    @Resource
    private FlowNodeRegisterMsgService flowNodeRegisterMsgService;

    @GetMapping("/state")
    public ResponseResult<FlowNodeStateResponseDTO> getFlowNodeState(@RequestParam String flowNodePubkey) {
        return ResponseResult.success(flowNodeRegisterMsgService.getFlowNodeState(ByteArrayUtil.hexToBytes(flowNodePubkey)));
    }
}
