package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/returning-flow-rate")
public class ReturningFlowRateController {

    @Resource
    private ConsumeChainService consumeChainService;

    @GetMapping("/{source}/{target}")
    public ResponseResult<ReturningFlowRateResponseDTO> getReturningFlowRate(
            @PathVariable String source,
            @PathVariable String target,
            @RequestParam(required = false, defaultValue = "0") long startTime,
            @RequestParam(required = false, defaultValue = "9223372036854775807") long endTime,
            @RequestParam(required = false, defaultValue = "1") short currencyType
    ) {
        ReturningFlowRateRequestDTO returningFlowRateRequestDTO = new ReturningFlowRateRequestDTO();
        returningFlowRateRequestDTO.setSource(ByteArrayUtil.hexToBytes(source));
        returningFlowRateRequestDTO.setTarget(ByteArrayUtil.hexToBytes(target));
        returningFlowRateRequestDTO.setStartTime(startTime);
        returningFlowRateRequestDTO.setEndTime(endTime);
        returningFlowRateRequestDTO.setCurrencyType(currencyType);

        // TODO： 把总滞留指数接口分开，算力消耗大
        ReturningFlowRateResponseDTO responseDTO = consumeChainService.getReturningFlowRate(returningFlowRateRequestDTO);
        return ResponseResult.success(responseDTO);
    }

}
