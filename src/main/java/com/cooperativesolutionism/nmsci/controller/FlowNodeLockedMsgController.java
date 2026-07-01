package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.dto.LockedMessageResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static com.cooperativesolutionism.nmsci.controller.ApiRequestBoundary.badRequestOnIllegalArgument;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;

@RestController
@RequestMapping("/flow-node-locks")
public class FlowNodeLockedMsgController {

    private final FlowNodeLockedMsgService flowNodeLockedMsgService;
    private final FlowNodeLockedMsgConverter flowNodeLockedMsgConverter;

    public FlowNodeLockedMsgController(
            FlowNodeLockedMsgService flowNodeLockedMsgService,
            FlowNodeLockedMsgConverter flowNodeLockedMsgConverter
    ) {
        this.flowNodeLockedMsgService = flowNodeLockedMsgService;
        this.flowNodeLockedMsgConverter = flowNodeLockedMsgConverter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<FlowNodeLockedMsg> saveFlowNodeLockedMsg(@RequestBody @ByteArraySize(FLOW_NODE_LOCKED_INBOUND_BYTES) byte[] byteData) {
        return badRequestOnIllegalArgument(() -> {
            FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgConverter.fromByteArray(byteData);
            return ResponseResult.success(flowNodeLockedMsgService.saveFlowNodeLockedMsg(flowNodeLockedMsg));
        });
    }

    @GetMapping("/{id}")
    public ResponseResult<FlowNodeLockedMsg> getFlowNodeLockedMsgById(@PathVariable("id") String id) {
        FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgService.getFlowNodeLockedMsgById(uuid(id));
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
        return badRequestOnIllegalArgument(() -> {
            Optional<FlowNodeLockedMsg> flowNodeLockedMsg = flowNodeLockedMsgService.findFlowNodeLockedMsgByFlowNodePubkey(hexBytesOrNull(flowNodePubkey));
            return ResponseResult.success(new LockedMessageResponseDTO<>(flowNodeLockedMsg.isPresent(), flowNodeLockedMsg.orElse(null)));
        });
    }
}
