package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/consume-chain")
public class ConsumeChainController {

    private static final Sort CONSUME_CHAIN_QUERY_SORT = Sort.by(Sort.Order.desc("tailMountTimestamp"), Sort.Order.desc("id"));

    @Resource
    private ConsumeChainService consumeChainService;

    @GetMapping("/by-mounted-transaction")
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> getConsumeChainByMountedTransaction(
            @RequestParam String relatedTransactionMount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<ConsumeChainResponseDTO> consumeChainResponseDTOs = consumeChainService.getConsumeChainByMountedTransaction(
                UUID.fromString(relatedTransactionMount),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(consumeChainResponseDTOs));
    }

    @GetMapping("/id/{id}")
    public ResponseResult<ConsumeChainResponseDTO> getConsumeChainById(@PathVariable String id) {
        ConsumeChainResponseDTO consumeChainResponseDTO = consumeChainService.getConsumeChainById(UUID.fromString(id));
        return ResponseResult.success(consumeChainResponseDTO);
    }

    @GetMapping("/by-start")
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> getConsumeChainByStart(
            @RequestParam String start,
            @RequestParam(required = false) Boolean isLoop,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<ConsumeChainResponseDTO> consumeChainResponseDTOs;
        if (isLoop == null) {
            consumeChainResponseDTOs = consumeChainService.getConsumeChainByStart(UUID.fromString(start), pageable(page, size));
        } else {
            consumeChainResponseDTOs = consumeChainService.getConsumeChainByStartAndIsLoop(UUID.fromString(start), isLoop, pageable(page, size));
        }

        return ResponseResult.success(SliceResponseDTO.from(consumeChainResponseDTOs));
    }

    @GetMapping("/by-end")
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> getConsumeChainByEnd(
            @RequestParam String end,
            @RequestParam(required = false) Boolean isLoop,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<ConsumeChainResponseDTO> consumeChainResponseDTOs;
        if (isLoop == null) {
            consumeChainResponseDTOs = consumeChainService.getConsumeChainByEnd(UUID.fromString(end), pageable(page, size));
        } else {
            consumeChainResponseDTOs = consumeChainService.getConsumeChainByEndAndIsLoop(UUID.fromString(end), isLoop, pageable(page, size));
        }

        return ResponseResult.success(SliceResponseDTO.from(consumeChainResponseDTOs));
    }

    private Pageable pageable(int page, int size) {
        return PageRequestUtil.of(page, size, CONSUME_CHAIN_QUERY_SORT);
    }
}
