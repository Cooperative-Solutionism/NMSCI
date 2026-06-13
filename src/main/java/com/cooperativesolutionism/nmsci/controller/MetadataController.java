package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.dto.CurrencyTypeMetadataDTO;
import com.cooperativesolutionism.nmsci.dto.MsgTypeMetadataDTO;
import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/metadata")
public class MetadataController {

    @GetMapping("/msg-types")
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
}
