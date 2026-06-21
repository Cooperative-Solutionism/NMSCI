package com.cooperativesolutionism.nmsci.task;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
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

    /**
     * 出块被禁用时只在首次跳过打一条日志，避免按出块周期反复刷屏。
     */
    private boolean loggedGenerationDisabled = false;

    private final BlockChainService blockChainService;
    private final NmsciMetrics nmsciMetrics;
    private final NmsciProperties nmsciProperties;

    public GenerateBlockTask(BlockChainService blockChainService, NmsciMetrics nmsciMetrics,
                             NmsciProperties nmsciProperties) {
        this.blockChainService = blockChainService;
        this.nmsciMetrics = nmsciMetrics;
        this.nmsciProperties = nmsciProperties;
    }

    /**
     * 定时任务：应用启动后立即开始生成区块，之后按配置的出块周期（{@code nmsci.block-interval-ms}，默认10分钟）生成区块
     */
    @Scheduled(initialDelay = 0, fixedDelayString = "${nmsci.block-interval-ms:600000}")
    public void execute() {
        if (!nmsciProperties.isBlockGenerationEnabled()) {
            // 出块开关关闭：进入「静默校验窗」。节点正常对外提供查询/校验，但不铸新块——
            // 用于升级部署时先完成 Flyway 迁移与存量链校验，确认无误后再开启出块，避免一启动就产出不可逆的新版本区块。
            if (!loggedGenerationDisabled) {
                logger.warn("出块已禁用(nmsci.block-generation-enabled=false)，定时出块任务将持续跳过；完成升级校验后置为 true 并重启即可开始出块");
                loggedGenerationDisabled = true;
            }
            return;
        }

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
