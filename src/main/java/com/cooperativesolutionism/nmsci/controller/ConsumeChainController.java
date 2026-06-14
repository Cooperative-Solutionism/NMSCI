package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainQueryService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
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
    private ConsumeChainQueryService consumeChainQueryService;

    @GetMapping("/by-mounted-transaction")
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> getConsumeChainByMountedTransaction(
            @RequestParam String relatedTransactionMount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<ConsumeChainResponseDTO> consumeChainResponseDTOs = consumeChainQueryService.getConsumeChainByMountedTransaction(
                UUID.fromString(relatedTransactionMount),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(consumeChainResponseDTOs));
    }

    @GetMapping("/id/{id}")
    public ResponseResult<ConsumeChainResponseDTO> getConsumeChainById(@PathVariable String id) {
        ConsumeChainResponseDTO consumeChainResponseDTO = consumeChainQueryService.getConsumeChainById(UUID.fromString(id));
        return ResponseResult.success(consumeChainResponseDTO);
    }

    @GetMapping("/by-id")
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> getConsumeChainByRelatedId(
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String node,
            @RequestParam(required = false) Boolean isLoop,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return consumeChainByRelatedId(start, end, node, isLoop, page, size);
    }

    @GetMapping("/by-pubkey")
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> getConsumeChainByPubkey(
            @RequestParam(required = false) String startPubkey,
            @RequestParam(required = false) String endPubkey,
            @RequestParam(required = false) String nodePubkey,
            @RequestParam(required = false) Boolean isLoop,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<ConsumeChainResponseDTO> consumeChainResponseDTOs = consumeChainQueryService.getConsumeChainByPubkey(
                pubkey(startPubkey),
                pubkey(endPubkey),
                pubkey(nodePubkey),
                isLoop,
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(consumeChainResponseDTOs));
    }

    private Pageable pageable(int page, int size) {
        return PageRequestUtil.of(page, size, CONSUME_CHAIN_QUERY_SORT);
    }

    private ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> consumeChainByRelatedId(
            String start,
            String end,
            String node,
            Boolean isLoop,
            int page,
            int size
    ) {
        Slice<ConsumeChainResponseDTO> consumeChainResponseDTOs = consumeChainQueryService.getConsumeChainByRelatedId(
                uuid(start),
                uuid(end),
                uuid(node),
                isLoop,
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(consumeChainResponseDTOs));
    }

    private UUID uuid(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return UUID.fromString(id);
    }

    private byte[] pubkey(String pubkey) {
        if (pubkey == null || pubkey.isBlank()) {
            return null;
        }
        return ByteArrayUtil.hexToBytes(pubkey);
    }
}
