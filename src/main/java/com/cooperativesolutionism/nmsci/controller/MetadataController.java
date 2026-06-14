package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.dto.BlockFormatMetadataDTO;
import com.cooperativesolutionism.nmsci.dto.CurrencyTypeMetadataDTO;
import com.cooperativesolutionism.nmsci.dto.DifficultyMetadataDTO;
import com.cooperativesolutionism.nmsci.dto.MsgTypeMetadataDTO;
import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/metadata")
public class MetadataController {

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private BlockChainService blockChainService;

    @GetMapping("/message-types")
    public ResponseResult<List<MsgTypeMetadataDTO>> getMsgTypes() {
        List<MsgTypeMetadataDTO> msgTypes = Arrays.stream(MsgTypeEnum.values())
                .map(MsgTypeMetadataDTO::from)
                .collect(Collectors.toList());
        return ResponseResult.success(msgTypes);
    }

    @GetMapping("/currency-types")
    public ResponseResult<List<CurrencyTypeMetadataDTO>> getCurrencyTypes() {
        List<CurrencyTypeMetadataDTO> currencyTypes = Arrays.stream(CurrencyTypeEnum.values())
                .map(CurrencyTypeMetadataDTO::from)
                .collect(Collectors.toList());
        return ResponseResult.success(currencyTypes);
    }

    @GetMapping("/block-format")
    public ResponseResult<BlockFormatMetadataDTO> getBlockFormat() {
        return ResponseResult.success(BlockFormatMetadataDTO.from(nmsciProperties));
    }

    @GetMapping("/difficulty")
    public ResponseResult<DifficultyMetadataDTO> getDifficulty() {
        BlockInfo latestBlock = blockChainService.getLastBlock();
        int registerNbits = latestBlock != null
                ? latestBlock.getRegisterDifficultyTarget()
                : nmsciProperties.getRegisterDifficultyTargetNbits();
        int transactionNbits = latestBlock != null
                ? latestBlock.getTransactionDifficultyTarget()
                : nmsciProperties.getTransactionDifficultyTargetNbits();
        return ResponseResult.success(DifficultyMetadataDTO.from(registerNbits, transactionNbits));
    }
}
