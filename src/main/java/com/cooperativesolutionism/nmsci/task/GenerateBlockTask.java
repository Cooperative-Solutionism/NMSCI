package com.cooperativesolutionism.nmsci.task;

import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GenerateBlockTask {

    private static final Logger logger = LoggerFactory.getLogger(GenerateBlockTask.class);

    /**
     * 是否为第一次运行
     * 如果是第一次运行，则需要将所有未装块的消息打包进区块
     */
    private boolean isFirstTimeRun = true;

    private final BlockChainService blockChainService;
    private final NmsciMetrics nmsciMetrics;

    public GenerateBlockTask(BlockChainService blockChainService, NmsciMetrics nmsciMetrics) {
        this.blockChainService = blockChainService;
        this.nmsciMetrics = nmsciMetrics;
    }

    /**
     * 定时任务：应用启动后立即开始生成区块，之后按配置的出块周期（{@code nmsci.block-interval-ms}，默认10分钟）生成区块
     */
    @Scheduled(initialDelay = 0, fixedDelayString = "${nmsci.block-interval-ms:600000}")
    public void execute() {
        long startTime = -1L;

        try {
            startTime = DateUtil.getCurrentMicros();
            logger.info("开始生成区块: {}", startTime);

            nmsciMetrics.timeBlockGeneration(blockChainService::generateBlock);

            if (isFirstTimeRun) {
                blockChainService.generateBlockUntilNoNotInBlockMsgs();
                isFirstTimeRun = false;
                logger.info("第一次运行，已将所有未装块的消息都进行装块");
            }

            long endTime = DateUtil.getCurrentMicros();
            logger.info("成功生成区块: {}", endTime);
            logger.info("生成区块用时: {} 毫秒", (endTime - startTime) / 1000);
        } catch (Exception ex) {
            nmsciMetrics.recordBlockGenerationError();
            logFailure(startTime, ex);
        }
    }

    private void logFailure(long startTime, Exception ex) {
        if (startTime < 0) {
            logger.error("生成区块失败", ex);
            return;
        }
        try {
            long endTime = DateUtil.getCurrentMicros();
            logger.error("生成区块失败，用时: {} 毫秒", (endTime - startTime) / 1000, ex);
        } catch (Exception timeEx) {
            logger.error("生成区块失败，且计算失败耗时失败", ex);
            logger.warn("计算失败耗时失败", timeEx);
        }
    }

}
