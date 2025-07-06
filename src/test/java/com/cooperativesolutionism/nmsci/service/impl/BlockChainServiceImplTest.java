package com.cooperativesolutionism.nmsci.service.impl;

import jakarta.annotation.Resource;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.security.Security;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class BlockChainServiceImplTest {

    // 注入BlockChainService
    @Resource
    private BlockChainServiceImpl blockChainService;

    @Test
    void generateBlock() {
        Security.addProvider(new BouncyCastleProvider());
        blockChainService.generateBlock();
    }
}