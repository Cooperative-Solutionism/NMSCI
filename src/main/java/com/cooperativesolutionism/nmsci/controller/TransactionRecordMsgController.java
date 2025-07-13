package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transaction-record-msg")
public class TransactionRecordMsgController {

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
    public ResponseResult<List<TransactionRecordMsg>> getTransactionRecordMsgByConsumeNodePubkey(@PathVariable("consumeNodePubkey") String consumeNodePubkey) {
        List<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.getTransactionRecordMsgByConsumeNodePubkey(ByteArrayUtil.base64ToBytes(consumeNodePubkey));
        return ResponseResult.success(transactionRecordMsgs);
    }

    @GetMapping("/flow-node-pubkey/{flowNodePubkey}")
    public ResponseResult<List<TransactionRecordMsg>> getTransactionRecordMsgByFlowNodePubkey(@PathVariable("flowNodePubkey") String flowNodePubkey) {
        List<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.getTransactionRecordMsgByFlowNodePubkey(ByteArrayUtil.base64ToBytes(flowNodePubkey));
        return ResponseResult.success(transactionRecordMsgs);
    }

    @GetMapping("/{consumeNodePubkey}/{flowNodePubkey}")
    public ResponseResult<List<TransactionRecordMsg>> getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
            @PathVariable("consumeNodePubkey") String consumeNodePubkey,
            @PathVariable("flowNodePubkey") String flowNodePubkey
    ) {
        List<TransactionRecordMsg> transactionRecordMsgs = transactionRecordMsgService.getTransactionRecordMsgByConsumeNodePubkeyAndFlowNodePubkey(
                ByteArrayUtil.base64ToBytes(consumeNodePubkey),
                ByteArrayUtil.base64ToBytes(flowNodePubkey)
        );
        return ResponseResult.success(transactionRecordMsgs);
    }
}
