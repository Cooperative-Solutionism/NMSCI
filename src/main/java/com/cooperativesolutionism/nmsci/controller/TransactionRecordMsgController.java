package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
