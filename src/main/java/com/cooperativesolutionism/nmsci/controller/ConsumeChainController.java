package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/consume-chain")
public class ConsumeChainController {

    @Resource
    private ConsumeChainService consumeChainService;

    @GetMapping("/by-mounted-transaction")
    public ResponseResult<List<ConsumeChainResponseDTO>> getConsumeChainByMountedTransaction(
            @RequestParam String relatedTransactionMount
    ) {
        List<ConsumeChainResponseDTO> consumeChainResponseDTOList = consumeChainService.getConsumeChainByMountedTransaction(UUID.fromString(relatedTransactionMount));
        return ResponseResult.success(consumeChainResponseDTOList);
    }

    @GetMapping("/id/{id}")
    public ResponseResult<ConsumeChainResponseDTO> getConsumeChainById(@PathVariable String id) {
        ConsumeChainResponseDTO consumeChainResponseDTO = consumeChainService.getConsumeChainById(UUID.fromString(id));
        return ResponseResult.success(consumeChainResponseDTO);
    }

    @GetMapping("/by-start")
    public ResponseResult<List<ConsumeChainResponseDTO>> getConsumeChainByStart(
            @RequestParam String start,
            @RequestParam(required = false) Boolean isLoop
    ) {
        List<ConsumeChainResponseDTO> consumeChainResponseDTOList;
        if (isLoop == null) {
            consumeChainResponseDTOList = consumeChainService.getConsumeChainByStart(UUID.fromString(start));
        } else {
            consumeChainResponseDTOList = consumeChainService.getConsumeChainByStartAndIsLoop(UUID.fromString(start), isLoop);
        }

        return ResponseResult.success(consumeChainResponseDTOList);
    }

    @GetMapping("/by-end")
    public ResponseResult<List<ConsumeChainResponseDTO>> getConsumeChainByEnd(
            @RequestParam String end,
            @RequestParam(required = false) Boolean isLoop
    ) {
        List<ConsumeChainResponseDTO> consumeChainResponseDTOList;
        if (isLoop == null) {
            consumeChainResponseDTOList = consumeChainService.getConsumeChainByEnd(UUID.fromString(end));
        } else {
            consumeChainResponseDTOList = consumeChainService.getConsumeChainByEndAndIsLoop(UUID.fromString(end), isLoop);
        }

        return ResponseResult.success(consumeChainResponseDTOList);
    }
}
