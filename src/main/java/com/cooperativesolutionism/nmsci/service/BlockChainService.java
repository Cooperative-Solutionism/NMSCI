package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.BlockInfo;

public interface BlockChainService {

    /**
     * 生成一个区块
     */
    void generateBlock();

    /**
     * 获取最新的区块信息
     */
    BlockInfo getLastBlock();

    /**
     * 根据区块高度获取区块信息
     *
     * @param height 区块高度
     * @return 区块信息
     */
    BlockInfo getBlockByHeight(long height);

    /**
     * 根据区块哈希获取区块信息
     *
     * @param hash 区块哈希
     * @return 区块信息
     */
    BlockInfo getBlockByHash(String hash);
}
