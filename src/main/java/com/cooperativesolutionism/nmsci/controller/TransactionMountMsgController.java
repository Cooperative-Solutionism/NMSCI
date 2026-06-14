package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transaction-mounts")
public class TransactionMountMsgController {

    @Resource
    private TransactionMountMsgService transactionMountMsgService;

    @Resource
    private TransactionMountMsgConverter transactionMountMsgConverter;

    @PostMapping
    public ResponseResult<TransactionMountMsg> saveTransactionMountMsg(@RequestBody @ByteArraySize(269) byte[] byteData) {
        TransactionMountMsg transactionMountMsg = transactionMountMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(transactionMountMsgService.saveTransactionMountMsg(transactionMountMsg));
    }

    @GetMapping("/{id}")
    public ResponseResult<TransactionMountMsg> getTransactionMountMsgById(@PathVariable String id) {
        TransactionMountMsg transactionMountMsg = transactionMountMsgService.getTransactionMountMsgById(UUID.fromString(id));
        return ResponseResult.success(transactionMountMsg);
    }

    @GetMapping
    public ResponseResult<SliceResponseDTO<TransactionMountMsg>> searchTransactionMountMsgs(
            @RequestParam(required = false) String consumeNodePubkey,
            @RequestParam(required = false) String flowNodePubkey,
            @RequestParam(required = false) String mountedTransactionRecordId,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.searchTransactionMountMsgs(
                hexToBytesOrNull(consumeNodePubkey),
                hexToBytesOrNull(flowNodePubkey),
                uuidOrNull(mountedTransactionRecordId),
                startTime,
                endTime,
                PageRequestUtil.ofMessageQuery(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionMountMsgs));
    }

    private static byte[] hexToBytesOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return UUID.fromString(value);
    }
}
