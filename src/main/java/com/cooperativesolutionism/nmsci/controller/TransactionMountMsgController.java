package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
