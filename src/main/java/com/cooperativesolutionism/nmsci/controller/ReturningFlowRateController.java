package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/returning-flow-rate")
public class ReturningFlowRateController {

    @Resource
    private ConsumeChainService consumeChainService;

    @GetMapping("/by-id")
    public ResponseResult<ReturningFlowRateResponseDTO> getReturningFlowRateByTarget(
            @RequestParam(required = false, defaultValue = "") String sourceId,
            @RequestParam String targetId,
            @RequestParam(required = false, defaultValue = "0") long startTime,
            @RequestParam(required = false, defaultValue = "9223372036854775807") long endTime,
            @RequestParam(required = false, defaultValue = "1") short currencyType
    ) {
        ReturningFlowRateRequestDTO returningFlowRateRequestDTO = new ReturningFlowRateRequestDTO();
        returningFlowRateRequestDTO.setTargetId(UUID.fromString(targetId));
        returningFlowRateRequestDTO.setStartTime(startTime);
        returningFlowRateRequestDTO.setEndTime(endTime);
        returningFlowRateRequestDTO.setCurrencyType(currencyType);

        ReturningFlowRateResponseDTO responseDTO;
        if (sourceId.isEmpty()) {
            responseDTO = consumeChainService.getReturningFlowRateByTargetId(returningFlowRateRequestDTO);
        } else {
            returningFlowRateRequestDTO.setSourceId(UUID.fromString(sourceId));
            responseDTO = consumeChainService.getReturningFlowRateById(returningFlowRateRequestDTO);
        }

        return ResponseResult.success(responseDTO);
    }

    @GetMapping("/by-pubkey")
    public ResponseResult<ReturningFlowRateResponseDTO> getReturningFlowRate(
            @RequestParam(required = false, defaultValue = "") String source,
            @RequestParam String target,
            @RequestParam(required = false, defaultValue = "0") long startTime,
            @RequestParam(required = false, defaultValue = "9223372036854775807") long endTime,
            @RequestParam(required = false, defaultValue = "1") short currencyType
    ) {
        ReturningFlowRateRequestDTO returningFlowRateRequestDTO = new ReturningFlowRateRequestDTO();
        returningFlowRateRequestDTO.setTarget(ByteArrayUtil.hexToBytes(target));
        returningFlowRateRequestDTO.setStartTime(startTime);
        returningFlowRateRequestDTO.setEndTime(endTime);
        returningFlowRateRequestDTO.setCurrencyType(currencyType);

        ReturningFlowRateResponseDTO responseDTO;
        if (source.isEmpty()) {
            responseDTO = consumeChainService.getReturningFlowRateByTargetPubkey(returningFlowRateRequestDTO);
            return ResponseResult.success(responseDTO);
        } else {
            returningFlowRateRequestDTO.setSource(ByteArrayUtil.hexToBytes(source));
            responseDTO = consumeChainService.getReturningFlowRateByPubkey(returningFlowRateRequestDTO);
        }

        return ResponseResult.success(responseDTO);
    }

}
