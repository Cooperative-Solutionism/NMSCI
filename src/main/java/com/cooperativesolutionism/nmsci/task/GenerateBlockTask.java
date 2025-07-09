package com.cooperativesolutionism.nmsci.task;

import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import jakarta.annotation.Resource;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.Security;

@Component
public class GenerateBlockTask {

    private static final Logger logger = LoggerFactory.getLogger(GenerateBlockTask.class);

    @Resource
    private BlockChainService blockChainService;

    /**
     * 应用启动后立即执行，然后每10分钟执行一次
     */
    @Scheduled(initialDelay = 0, fixedDelay = 10 * 60 * 1000)
    public void execute() {
        Security.addProvider(new BouncyCastleProvider());

        // 记录任务执行时间
        long startTime = DateUtil.getCurrentMicros();
        logger.info("开始生成区块: {}", startTime);

        // 生成区块
        blockChainService.generateBlock();

        // 计算任务执行时间
        long endTime = DateUtil.getCurrentMicros();
        logger.info("成功生成区块: {}", endTime);
        logger.info("生成区块用时: {} 毫秒", (endTime - startTime) / 1000);
    }

}
