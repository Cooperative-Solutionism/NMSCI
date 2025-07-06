package com.cooperativesolutionism.nmsci.task;

import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GenerateBlockTask {

    @Resource
    private BlockChainService blockChainService;

    /**
     * 应用启动后立即执行，然后每10分钟执行一次
     */
    @Scheduled(initialDelay = 0, fixedDelay = 10 * 60 * 1000)
    public void execute() {
        // 记录任务执行时间
        long startTime = DateUtil.getCurrentMicros();
        System.out.println("GenerateBlockTask execute: " + startTime);

        // 生成区块
        blockChainService.generateBlock();

        // 计算任务执行时间
        long endTime = DateUtil.getCurrentMicros();
        System.out.println("GenerateBlockTask execute end: " + endTime);
        System.out.println("GenerateBlockTask execute time: " + (endTime - startTime) + " micros");
    }

}
