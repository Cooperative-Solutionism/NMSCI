package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;

@RestController
@RequestMapping("/flow-node-registrations")
public class FlowNodeRegisterMsgController {

    // FlowNodeRegisterMsg 不是 ConfirmableMessage（无 confirmTimestamp），按 id 排序（与 /flow-nodes 列表一致）。
    private static final Sort REGISTER_QUERY_SORT = Sort.by(Sort.Order.asc("id"));

    @Resource
    private FlowNodeRegisterMsgService flowNodeRegisterMsgMsgService;

    @Resource
    private FlowNodeRegisterMsgConverter flowNodeRegisterMsgConverter;

    @PostMapping
    public ResponseResult<FlowNodeRegisterMsg> saveFlowNodeRegisterMsg(@RequestBody @ByteArraySize(FLOW_NODE_REGISTER_INBOUND_BYTES) byte[] byteData) {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = flowNodeRegisterMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeRegisterMsgMsgService.saveFlowNodeRegisterMsg(flowNodeRegisterMsgMsg));
    }

    @GetMapping("/{id}")
    public ResponseResult<FlowNodeRegisterMsg> getFlowNodeRegisterMsgById(@PathVariable("id") String id) {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = flowNodeRegisterMsgMsgService.getFlowNodeRegisterMsgById(UUID.fromString(id));
        return ResponseResult.success(flowNodeRegisterMsgMsg);
    }

    @GetMapping
    public ResponseResult<SliceResponseDTO<FlowNodeRegisterMsg>> listFlowNodeRegisterMsgs(
            @RequestParam(required = false) String flowNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<FlowNodeRegisterMsg> flowNodeRegisterMsgs = flowNodeRegisterMsgMsgService.listFlowNodeRegisterMsgs(
                hexBytesOrNull(flowNodePubkey),
                PageRequestUtil.of(page, size, REGISTER_QUERY_SORT)
        );
        return ResponseResult.success(SliceResponseDTO.from(flowNodeRegisterMsgs));
    }
}
