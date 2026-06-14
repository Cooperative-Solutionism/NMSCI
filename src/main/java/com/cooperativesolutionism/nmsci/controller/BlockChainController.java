package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/blocks")
public class BlockChainController {

    @Resource
    private BlockChainService blockChainService;

    @GetMapping("/latest")
    public ResponseResult<BlockInfo> getLastBlock() {
        return ResponseResult.success(blockChainService.getLastBlock());
    }

    @GetMapping("/{height}")
    public ResponseResult<BlockInfo> getBlockByHeight(@PathVariable long height) {
        return ResponseResult.success(blockChainService.getBlockByHeight(height));
    }

    @GetMapping(params = "hash")
    public ResponseResult<BlockInfo> getBlockByHash(@RequestParam String hash) {
        return ResponseResult.success(blockChainService.getBlockByHash(hash));
    }

}
