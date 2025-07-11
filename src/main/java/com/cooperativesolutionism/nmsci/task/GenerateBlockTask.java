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

    /**
     * 是否为第一次运行
     * 如果是第一次运行，则需要将所有未装块的消息打包进区块
     */
    private boolean isFirstTimeRun = true;

    @Resource
    private BlockChainService blockChainService;

    /**
     * 定时任务：应用启动后立即开始生成区块，之后每10分钟开始生成区块
     */
    @Scheduled(initialDelay = 0, fixedDelay = 10 * 60 * 1000)
    public void execute() {
        Security.addProvider(new BouncyCastleProvider());

        // 记录任务执行时间
        long startTime = DateUtil.getCurrentMicros();
        logger.info("开始生成区块: {}", startTime);

        // 如果是第一次运行，则需要将所有未装块的消息打包进区块
        if (isFirstTimeRun) {
            blockChainService.generateBlockUntilNoNotInBlockMsgs();
            isFirstTimeRun = false;
            logger.info("第一次运行，已将所有未装块的消息打包进区块");
        }

        // 如果不是第一次运行，则直接生成一个新的区块
        if (!isFirstTimeRun) {
            blockChainService.generateBlock();
        }

        // 计算任务执行时间
        long endTime = DateUtil.getCurrentMicros();
        logger.info("成功生成区块: {}", endTime);
        logger.info("生成区块用时: {} 毫秒", (endTime - startTime) / 1000);
    }

}
