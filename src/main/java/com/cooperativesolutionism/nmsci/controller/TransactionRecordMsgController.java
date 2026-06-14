package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transaction-records")
public class TransactionRecordMsgController {

    @Resource
    private TransactionRecordMsgService transactionRecordMsgService;

    @Resource
    private TransactionRecordMsgConverter transactionRecordMsgConverter;

    @PostMapping
    public ResponseResult<TransactionRecordMsg> saveTransactionRecordMsg(@RequestBody @ByteArraySize(263) byte[] byteData) {
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(transactionRecordMsgService.saveTransactionRecordMsg(transactionRecordMsg));
    }

    @GetMapping("/{id}")
    public ResponseResult<TransactionRecordMsg> getTransactionRecordMsgById(@PathVariable("id") String id) {
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgService.getTransactionRecordMsgById(UUID.fromString(id));
        return ResponseResult.success(transactionRecordMsg);
    }

    @GetMapping
    public ResponseResult<SliceResponseDTO<TransactionRecordMsg>> searchTransactionRecordMsgs(
            @RequestParam(required = false) String consumeNodePubkey,
            @RequestParam(required = false) String flowNodePubkey,
            @RequestParam(required = false) Short currencyType,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.searchTransactionRecordMsgs(
                hexToBytesOrNull(consumeNodePubkey),
                hexToBytesOrNull(flowNodePubkey),
                currencyType,
                startTime,
                endTime,
                PageRequestUtil.ofMessageQuery(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionRecordMsgs));
    }

    private static byte[] hexToBytesOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }
}
