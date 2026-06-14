package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateRequestDTO;
import com.cooperativesolutionism.nmsci.dto.ReturningFlowRateResponseDTO;
import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainQueryService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/returning-flow-rates")
public class ReturningFlowRateController {

    @Resource
    private ConsumeChainQueryService consumeChainQueryService;

    /**
     * 回流率查询（统一集合根）：target 必填（targetId 或 targetPubkey），source 可选；
     * 缺省 source 时返回流入 target 的总回流率。id 与 pubkey 参数不可混用。
     */
    @GetMapping
    public ResponseResult<ReturningFlowRateResponseDTO> getReturningFlowRate(
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String sourcePubkey,
            @RequestParam(required = false) String targetPubkey,
            @RequestParam(required = false, defaultValue = "0") long startTime,
            @RequestParam(required = false, defaultValue = "9223372036854775807") long endTime,
            @RequestParam(required = false, defaultValue = "1") short currencyType
    ) {
        boolean hasId = notBlank(sourceId) || notBlank(targetId);
        boolean hasPubkey = notBlank(sourcePubkey) || notBlank(targetPubkey);
        if (hasId && hasPubkey) {
            throw new BadRequestException("id 与 pubkey 查询参数不能混用");
        }

        ReturningFlowRateRequestDTO request = new ReturningFlowRateRequestDTO();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setCurrencyType(currencyType);

        ReturningFlowRateResponseDTO responseDTO;
        if (hasPubkey) {
            if (!notBlank(targetPubkey)) {
                throw new BadRequestException("targetPubkey 不能为空");
            }
            request.setTarget(ByteArrayUtil.hexToBytes(targetPubkey));
            if (notBlank(sourcePubkey)) {
                request.setSource(ByteArrayUtil.hexToBytes(sourcePubkey));
                responseDTO = consumeChainQueryService.getReturningFlowRateByPubkey(request);
            } else {
                responseDTO = consumeChainQueryService.getReturningFlowRateByTargetPubkey(request);
            }
        } else {
            if (!notBlank(targetId)) {
                throw new BadRequestException("targetId 不能为空");
            }
            request.setTargetId(UUID.fromString(targetId));
            if (notBlank(sourceId)) {
                request.setSourceId(UUID.fromString(sourceId));
                responseDTO = consumeChainQueryService.getReturningFlowRateById(request);
            } else {
                responseDTO = consumeChainQueryService.getReturningFlowRateByTargetId(request);
            }
        }

        return ResponseResult.success(responseDTO);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
