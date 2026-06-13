package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.dto.SystemParamsDTO;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system")
public class SystemController {

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private BlockChainService blockChainService;

    @GetMapping("/params")
    public ResponseResult<SystemParamsDTO> getParams() {
        return ResponseResult.success(SystemParamsDTO.from(nmsciProperties, blockChainService.getLastBlock()));
    }
}
