package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.dto.SliceResponseDTO;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import com.cooperativesolutionism.nmsci.util.PageRequestUtil;
import jakarta.annotation.Resource;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import static com.cooperativesolutionism.nmsci.controller.ApiRequestBoundary.badRequestOnIllegalArgument;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.compressedPubkeyHexOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;

@RestController
@RequestMapping("/transaction-mounts")
public class TransactionMountMsgController {

    @Resource
    private TransactionMountMsgService transactionMountMsgService;

    @Resource
    private TransactionMountMsgConverter transactionMountMsgConverter;

    @PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseResult<TransactionMountMsg> saveTransactionMountMsg(@RequestBody @ByteArraySize(TRANSACTION_MOUNT_INBOUND_BYTES) byte[] byteData) {
        return badRequestOnIllegalArgument(() -> {
            TransactionMountMsg transactionMountMsg = transactionMountMsgConverter.fromByteArray(byteData);
            return ResponseResult.success(transactionMountMsgService.saveTransactionMountMsg(transactionMountMsg));
        });
    }

    @GetMapping("/{id}")
    public ResponseResult<TransactionMountMsg> getTransactionMountMsgById(@PathVariable String id) {
        TransactionMountMsg transactionMountMsg = transactionMountMsgService.getTransactionMountMsgById(uuid(id));
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
                compressedPubkeyHexOrNull(consumeNodePubkey),
                compressedPubkeyHexOrNull(flowNodePubkey),
                uuidOrNull(mountedTransactionRecordId),
                startTime,
                endTime,
                PageRequestUtil.ofMessageQuery(page, size)
        );
        return ResponseResult.success(SliceResponseDTO.from(transactionMountMsgs));
    }
}
