package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/block-chain")
public class BlockChainController {

    @Resource
    private BlockChainService blockChainService;

    @GetMapping("/last")
    public ResponseResult<BlockInfo> getLastBlock() {
        return ResponseResult.success(blockChainService.getLastBlock());
    }

    @GetMapping("/height/{height}")
    public ResponseResult<BlockInfo> getBlockByHeight(long height) {
        return ResponseResult.success(blockChainService.getBlockByHeight(height));
    }

    @GetMapping("/hash/{hash}")
    public ResponseResult<BlockInfo> getBlockByHash(String hash) {
        return ResponseResult.success(blockChainService.getBlockByHash(hash));
    }

}
