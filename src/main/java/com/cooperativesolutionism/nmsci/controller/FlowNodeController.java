package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO;
import com.cooperativesolutionism.nmsci.dto.FlowNodeStateResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.cooperativesolutionism.nmsci.controller.ApiRequestBoundary.badRequestOnIllegalArgument;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytes;

@RestController
@RequestMapping("/flow-nodes")
public class FlowNodeController {

    private static final Sort FLOW_NODE_QUERY_SORT = Sort.by(Sort.Order.asc("id"));

    private final FlowNodeRegisterMsgService flowNodeRegisterMsgService;

    public FlowNodeController(FlowNodeRegisterMsgService flowNodeRegisterMsgService) {
        this.flowNodeRegisterMsgService = flowNodeRegisterMsgService;
    }

    @GetMapping("/{flowNodePubkey}")
    public ResponseResult<FlowNodeStateResponseDTO> getFlowNodeState(@PathVariable String flowNodePubkey) {
        return badRequestOnIllegalArgument(
                () -> ResponseResult.success(flowNodeRegisterMsgService.getFlowNodeState(hexBytes(flowNodePubkey)))
        );
    }

    @GetMapping
    public ResponseResult<SliceResponseDTO<FlowNodeListItemDTO>> listFlowNodes(
            @RequestParam(required = false, defaultValue = "true") Boolean registered,
            @RequestParam(required = false) Boolean authorized,
            @RequestParam(required = false) Boolean locked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Pageable pageable = PageRequestUtil.of(page, size, FLOW_NODE_QUERY_SORT);
        return ResponseResult.success(SliceResponseDTO.from(
                flowNodeRegisterMsgService.listFlowNodes(registered, authorized, locked, pageable)
        ));
    }
}
