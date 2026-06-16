package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.dto.LockedMessageResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;

@RestController
@RequestMapping("/flow-node-locks")
public class FlowNodeLockedMsgController {

    @Resource
    private FlowNodeLockedMsgService flowNodeLockedMsgService;

    @Resource
    private FlowNodeLockedMsgConverter flowNodeLockedMsgConverter;

    @PostMapping
    public ResponseResult<FlowNodeLockedMsg> saveFlowNodeLockedMsg(@RequestBody @ByteArraySize(FLOW_NODE_LOCKED_INBOUND_BYTES) byte[] byteData) {
        FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeLockedMsgService.saveFlowNodeLockedMsg(flowNodeLockedMsg));
    }

    @GetMapping("/{id}")
    public ResponseResult<FlowNodeLockedMsg> getFlowNodeLockedMsgById(@PathVariable("id") String id) {
        FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgService.getFlowNodeLockedMsgById(UUID.fromString(id));
        return ResponseResult.success(flowNodeLockedMsg);
    }

    @GetMapping
    public ResponseResult<SliceResponseDTO<FlowNodeLockedMsg>> listFlowNodeLockedMsgs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<FlowNodeLockedMsg> flowNodeLockedMsgs = flowNodeLockedMsgService.listFlowNodeLockedMsgs(
                PageRequestUtil.ofMessageQuery(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(flowNodeLockedMsgs));
    }

    @GetMapping("/status")
    public ResponseResult<LockedMessageResponseDTO<FlowNodeLockedMsg>> getFlowNodeLockStatus(@RequestParam String flowNodePubkey) {
        Optional<FlowNodeLockedMsg> flowNodeLockedMsg = flowNodeLockedMsgService.findFlowNodeLockedMsgByFlowNodePubkey(hexBytesOrNull(flowNodePubkey));
        return ResponseResult.success(new LockedMessageResponseDTO<>(flowNodeLockedMsg.isPresent(), flowNodeLockedMsg.orElse(null)));
    }
}
