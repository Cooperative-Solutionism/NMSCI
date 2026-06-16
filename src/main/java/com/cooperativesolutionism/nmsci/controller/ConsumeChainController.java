package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.ConsumeChainResponseDTO;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.ConsumeChainQueryService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.notBlank;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;

@RestController
@RequestMapping("/consume-chains")
public class ConsumeChainController {

    private static final Sort CONSUME_CHAIN_QUERY_SORT = Sort.by(Sort.Order.desc("tailMountTimestamp"), Sort.Order.desc("id"));
    private static final Sort CONSUME_CHAIN_EDGE_QUERY_SORT = Sort.by(
            Sort.Order.desc("relatedTransactionMountTimestamp"),
            Sort.Order.desc("id")
    );

    @Resource
    private ConsumeChainQueryService consumeChainQueryService;

    @GetMapping("/{id}")
    public ResponseResult<ConsumeChainResponseDTO> getConsumeChainById(@PathVariable String id) {
        ConsumeChainResponseDTO consumeChainResponseDTO = consumeChainQueryService.getConsumeChainById(UUID.fromString(id));
        return ResponseResult.success(consumeChainResponseDTO);
    }

    /**
     * 消费链集合查询（统一集合根）：按节点 id 或 pubkey 过滤（start/end/node 三选一，由服务层校验），
     * 或按挂载交易 id 过滤；id 与 pubkey 参数不可混用。
     */
    @GetMapping
    public ResponseResult<SliceResponseDTO<ConsumeChainResponseDTO>> queryConsumeChains(
            @RequestParam(required = false) String startId,
            @RequestParam(required = false) String endId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false) String startPubkey,
            @RequestParam(required = false) String endPubkey,
            @RequestParam(required = false) String nodePubkey,
            @RequestParam(required = false) Boolean isLoop,
            @RequestParam(required = false) String mountedTransactionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        boolean hasIdNode = notBlank(startId) || notBlank(endId) || notBlank(nodeId);
        boolean hasPubkeyNode = notBlank(startPubkey) || notBlank(endPubkey) || notBlank(nodePubkey);
        boolean hasMounted = notBlank(mountedTransactionId);

        if (hasIdNode && hasPubkeyNode) {
            throw new BadRequestException("id 与 pubkey 查询参数不能混用");
        }
        if (hasMounted && (hasIdNode || hasPubkeyNode)) {
            throw new BadRequestException("mountedTransactionId 不能与节点过滤参数混用");
        }

        Pageable pageable = PageRequestUtil.of(page, size, CONSUME_CHAIN_QUERY_SORT);

        Slice<ConsumeChainResponseDTO> result;
        if (hasMounted) {
            result = consumeChainQueryService.getConsumeChainByMountedTransaction(uuidOrNull(mountedTransactionId), pageable);
        } else if (hasPubkeyNode) {
            result = consumeChainQueryService.getConsumeChainByPubkey(
                    hexBytesOrNull(startPubkey), hexBytesOrNull(endPubkey), hexBytesOrNull(nodePubkey), isLoop, pageable);
        } else {
            result = consumeChainQueryService.getConsumeChainByRelatedId(
                    uuidOrNull(startId), uuidOrNull(endId), uuidOrNull(nodeId), isLoop, pageable);
        }
        return ResponseResult.success(SliceResponseDTO.from(result));
    }

    /**
     * 消费链边查询：target 必填（targetId 或 targetPubkey），source 可选；
     * 缺省 source 时返回流入 target 的全部边。id 与 pubkey 参数不可混用。返回按 chain 去重的边列表。
     */
    @GetMapping("/edges")
    public ResponseResult<SliceResponseDTO<ConsumeChainEdge>> getConsumeChainEdges(
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String sourcePubkey,
            @RequestParam(required = false) String targetPubkey,
            @RequestParam(required = false, defaultValue = "1") short currencyType,
            @RequestParam(required = false, defaultValue = "0") long startTime,
            @RequestParam(required = false, defaultValue = "9223372036854775807") long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        boolean hasId = notBlank(sourceId) || notBlank(targetId);
        boolean hasPubkey = notBlank(sourcePubkey) || notBlank(targetPubkey);
        if (hasId && hasPubkey) {
            throw new BadRequestException("id 与 pubkey 查询参数不能混用");
        }

        if (hasPubkey) {
            if (!notBlank(targetPubkey)) {
                throw new BadRequestException("targetPubkey 不能为空");
            }
        } else if (!notBlank(targetId)) {
            throw new BadRequestException("targetId 不能为空");
        }

        Pageable pageable = PageRequestUtil.of(page, size, CONSUME_CHAIN_EDGE_QUERY_SORT);
        Slice<ConsumeChainEdge> edges;
        if (hasPubkey) {
            edges = consumeChainQueryService.getConsumeChainEdgesByPubkey(
                    hexBytesOrNull(sourcePubkey), hexBytesOrNull(targetPubkey), currencyType, startTime, endTime, pageable);
        } else {
            edges = consumeChainQueryService.getConsumeChainEdgesById(
                    uuidOrNull(sourceId), uuidOrNull(targetId), currencyType, startTime, endTime, pageable);
        }
        return ResponseResult.success(SliceResponseDTO.from(edges));
    }
}
