package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.block.BlockFileStore;
import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.dto.StorageStatusDTO;
import com.cooperativesolutionism.nmsci.dto.SystemParamsDTO;
import com.cooperativesolutionism.nmsci.dto.SystemStatusDTO;
import com.cooperativesolutionism.nmsci.model.BlockInfoSummary;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemController {

    private final NmsciProperties nmsciProperties;
    private final BlockChainService blockChainService;
    private final MsgAbstractService msgAbstractService;
    private final CentralPubkeyLockedMsgService centralPubkeyLockedMsgService;
    private final BlockFileStore blockFileStore;

    public SystemController(
            NmsciProperties nmsciProperties,
            BlockChainService blockChainService,
            MsgAbstractService msgAbstractService,
            CentralPubkeyLockedMsgService centralPubkeyLockedMsgService,
            BlockFileStore blockFileStore
    ) {
        this.nmsciProperties = nmsciProperties;
        this.blockChainService = blockChainService;
        this.msgAbstractService = msgAbstractService;
        this.centralPubkeyLockedMsgService = centralPubkeyLockedMsgService;
        this.blockFileStore = blockFileStore;
    }

    @GetMapping("/params")
    public ResponseResult<SystemParamsDTO> getParams() {
        return ResponseResult.success(SystemParamsDTO.from(nmsciProperties, blockChainService.getLastBlockSummary()));
    }

    @GetMapping("/status")
    public ResponseResult<SystemStatusDTO> getStatus() {
        BlockInfoSummary latestBlock = blockChainService.getLastBlockSummary();
        byte[] currentCentralPubkey = ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPubkeyBase64());
        boolean currentCentralPubkeyLocked = centralPubkeyLockedMsgService
                .findCentralPubkeyLockedMsgByCentralPubkey(currentCentralPubkey)
                .isPresent();
        return ResponseResult.success(SystemStatusDTO.from(
                latestBlock,
                msgAbstractService.countPending(),
                msgAbstractService.findOldestPendingConfirmTimestamp(),
                nmsciProperties.getBlockIntervalMs(),
                currentCentralPubkeyLocked
        ));
    }

    @GetMapping("/storage")
    public ResponseResult<StorageStatusDTO> getStorage() {
        BlockFileStore.DatStorageInfo info = blockFileStore.datStorageInfo();
        return ResponseResult.success(StorageStatusDTO.from(
                info.datDirectory(),
                info.fileCount(),
                info.currentFileName(),
                info.currentFileSizeBytes(),
                info.totalBytes(),
                info.maxSizePerFileBytes()
        ));
    }
}
