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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/transaction-record-msg")
public class TransactionRecordMsgController {

    private static final Sort MESSAGE_QUERY_SORT = Sort.by(Sort.Order.desc("confirmTimestamp"), Sort.Order.desc("id"));

    @Resource
    private TransactionRecordMsgService transactionRecordMsgService;

    @PostMapping("/send")
    public ResponseResult<TransactionRecordMsg> saveTransactionRecordMsg(@RequestBody @ByteArraySize(263) byte[] byteData) {
        TransactionRecordMsg transactionRecordMsg = TransactionRecordMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(transactionRecordMsgService.saveTransactionRecordMsg(transactionRecordMsg));
    }

    @GetMapping("/id/{id}")
    public ResponseResult<TransactionRecordMsg> getTransactionRecordMsgById(@PathVariable("id") String id) {
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgService.getTransactionRecordMsgById(UUID.fromString(id));
        return ResponseResult.success(transactionRecordMsg);
    }

    @GetMapping("/consume-node-pubkey/{consumeNodePubkey}")
    public ResponseResult<SliceResponseDTO<TransactionRecordMsg>> getTransactionRecordMsgByConsumeNodePubkey(
            @PathVariable("consumeNodePubkey") String consumeNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.getTransactionRecordMsgByConsumeNodePubkey(
                ByteArrayUtil.hexToBytes(consumeNodePubkey),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionRecordMsgs));
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<SliceResponseDTO<TransactionRecordMsg>> getTransactionRecordMsgByFlowNodePubkey(
            @PathVariable("flowNodePubkey") String flowNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.getTransactionRecordMsgByFlowNodePubkey(
                ByteArrayUtil.hexToBytes(flowNodePubkey),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionRecordMsgs));
    }

    @GetMapping("/{consumeNodePubkey}/{flowNodePubkey}")
    public ResponseResult<SliceResponseDTO<TransactionRecordMsg>> getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
            @PathVariable("consumeNodePubkey") String consumeNodePubkey,
            @PathVariable("flowNodePubkey") String flowNodePubkey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        Slice<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
                ByteArrayUtil.hexToBytes(consumeNodePubkey),
                ByteArrayUtil.hexToBytes(flowNodePubkey),
                pageable(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionRecordMsgs));
    }

    private Pageable pageable(int page, int size) {
        return PageRequestUtil.of(page, size, MESSAGE_QUERY_SORT);
    }
}
