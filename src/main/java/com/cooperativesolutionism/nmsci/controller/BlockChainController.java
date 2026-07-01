package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.cooperativesolutionism.nmsci.controller.ApiRequestBoundary.badRequestOnIllegalArgument;

@RestController
@RequestMapping("/blocks")
public class BlockChainController {

    private final BlockChainService blockChainService;

    public BlockChainController(BlockChainService blockChainService) {
        this.blockChainService = blockChainService;
    }

    @GetMapping("/latest")
    public ResponseResult<BlockInfo> getLastBlock() {
        BlockInfo lastBlock = blockChainService.getLastBlock();
        if (lastBlock == null) {
            throw new NotFoundException("区块链尚未初始化，暂无区块");
        }
        return ResponseResult.success(lastBlock);
    }

    @GetMapping("/{height}")
    public ResponseResult<BlockInfo> getBlockByHeight(@PathVariable long height) {
        return ResponseResult.success(blockChainService.getBlockByHeight(height));
    }

    @GetMapping(params = "hash")
    public ResponseResult<BlockInfo> getBlockByHash(@RequestParam String hash) {
        return badRequestOnIllegalArgument(() -> ResponseResult.success(blockChainService.getBlockByHash(hash)));
    }

}
