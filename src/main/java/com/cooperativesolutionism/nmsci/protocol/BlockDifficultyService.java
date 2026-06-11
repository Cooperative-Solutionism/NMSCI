package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class BlockDifficultyService {

    private final BlockInfoRepository blockInfoRepository;
    private final Timer transactionDifficultyLookupTimer;

    public BlockDifficultyService(BlockInfoRepository blockInfoRepository, MeterRegistry meterRegistry) {
        this.blockInfoRepository = blockInfoRepository;
        this.transactionDifficultyLookupTimer = Timer.builder("nmsci.block.difficulty.lookup")
                .description("Latest block difficulty lookup latency")
                .tag("type", "transaction")
                .register(meterRegistry);
    }

    public int currentTransactionDifficultyTarget() {
        return transactionDifficultyLookupTimer.record(() ->
                blockInfoRepository.findTopByOrderByHeightDesc().getTransactionDifficultyTarget()
        );
    }
}
