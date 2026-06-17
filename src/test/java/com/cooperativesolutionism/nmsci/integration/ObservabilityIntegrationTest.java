package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 可观测性接线验证：Prometheus 抓取端点暴露业务指标，且真实出块会被指标记录。
 * （精确数值断言见 {@code NmsciMetricsTest} 单元测试。）
 */
@AutoConfigureObservability
class ObservabilityIntegrationTest extends NmsciIntegrationTestBase {

    @Resource
    private BlockChainService blockChainService;

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Test
    void prometheusEndpointExposesBusinessMetrics() throws Exception {
        // 预热一次请求，使内置 http.server.requests 指标完成绑定
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("nmsci_mempool_pending"), "应暴露待入块积压指标");
        assertTrue(body.contains("nmsci_block_generation"), "应暴露出块耗时指标");
        assertTrue(body.contains("nmsci_block_size_bytes"), "应暴露区块大小指标");
        assertTrue(body.contains("http_server_requests"), "应暴露内置 HTTP 请求指标");
    }

    @Test
    void recordsHeightMetricOnRealBlockGeneration() throws Exception {
        long expectedHeight = blockInfoRepository.findTopByOrderByHeightDesc().getHeight() + 1;

        blockChainService.generateBlock();

        String body = mockMvc.perform(get("/actuator/metrics/nmsci.block.height"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("nmsci.block.height"))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("\"value\":" + (double) expectedHeight),
                () -> "区块高度指标应等于 " + expectedHeight + "，实际响应: " + body);
    }
}
