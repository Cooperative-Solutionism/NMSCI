package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transaction-mount-msg")
public class TransactionMountMsgController {
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
    public ResponseResult<List<TransactionMountMsg>> getTransactionMountMsgByConsumeNodePubkey(@PathVariable String consumeNodePubkey) {
        List<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.getTransactionMountMsgByConsumeNodePubkey(ByteArrayUtil.base64ToBytes(consumeNodePubkey));
        return ResponseResult.success(transactionMountMsgs);
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<List<TransactionMountMsg>> getTransactionMountMsgByFlowNodePubkey(@PathVariable String flowNodePubkey) {
        List<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.getTransactionMountMsgByFlowNodePubkey(ByteArrayUtil.base64ToBytes(flowNodePubkey));
        return ResponseResult.success(transactionMountMsgs);
    }

    @GetMapping("/{consumeNodePubkey}/{flowNodePubkey}")
    public ResponseResult<List<TransactionMountMsg>> getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(
            @PathVariable String consumeNodePubkey,
            @PathVariable String flowNodePubkey
    ) {
        List<TransactionMountMsg> transactionMountMsgs = transactionMountMsgService.getTransactionMountMsgByConsumeNodePubkeyAndFlowNodePubkey(
                ByteArrayUtil.base64ToBytes(consumeNodePubkey),
                ByteArrayUtil.base64ToBytes(flowNodePubkey)
        );
        return ResponseResult.success(transactionMountMsgs);
    }
}
