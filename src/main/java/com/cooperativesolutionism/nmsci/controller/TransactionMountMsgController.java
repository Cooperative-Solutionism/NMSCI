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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transaction-mount-msg")
public class TransactionMountMsgController {
    private static final Sort MESSAGE_QUERY_SORT = Sort.by(Sort.Order.desc("confirmTimestamp"), Sort.Order.desc("id"));

    @Resource
    private TransactionMountMsgService transactionMountMsgService;

    @PostMapping("/send")
    public ResponseResult<TransactionMountMsg> saveTransactionMountMsg(@RequestBody @ByteArraySize(269) byte[] byteData) {
        TransactionMountMsg transactionMountMsg = TransactionMountMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(transactionMountMsgService.saveTransactionMountMsg(transactionMountMsg));
    }

    @GetMapping("/id/{id}")
    public ResponseResult<TransactionMountMsg> getTransactionMountMsgById(@PathVariable String id) {
        TransactionMountMsg transactionMountMsg = transactionMountMsgService.getTransactionMountMsgById(UUID.fromString(id));
        return ResponseResult.success(transactionMountMsg);
    }

    @GetMapping("/mounted-transaction-record-id/{id}")
    public ResponseResult<TransactionMountMsg> getTransactionMountMsgByMountedTransactionRecordId(@PathVariable String id) {
        TransactionMountMsg transactionMountMsg = transactionMountMsgService.getTransactionMountMsgByMountedTransactionRecordId(UUID.fromString(id));
        return ResponseResult.success(transactionMountMsg);
    }

    @GetMapping("/consume-node-pubkey/{consumeNodePubkey}")
    public ResponseResult<SliceResponseDTO<TransactionMountMsg>> getTransactionMountMsgByConsumeNodePubkey(
            @PathVariable String consumeNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.getTransactionMountMsgByConsumeNodePubkey(
                ByteArrayUtil.hexToBytes(consumeNodePubkey),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionMountMsgs));
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<SliceResponseDTO<TransactionMountMsg>> getTransactionMountMsgByFlowNodePubkey(
            @PathVariable String flowNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.getTransactionMountMsgByFlowNodePubkey(
                ByteArrayUtil.hexToBytes(flowNodePubkey),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionMountMsgs));
    }

    @GetMapping("/{consumeNodePubkey}/{flowNodePubkey}")
    public ResponseResult<SliceResponseDTO<TransactionMountMsg>> getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(
            @PathVariable String consumeNodePubkey,
            @PathVariable String flowNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(
                ByteArrayUtil.hexToBytes(consumeNodePubkey),
                ByteArrayUtil.hexToBytes(flowNodePubkey),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionMountMsgs));
    }

    private Pageable pageable(int page, int size) {
        return PageRequestUtil.of(page, size, MESSAGE_QUERY_SORT);
    }
}
