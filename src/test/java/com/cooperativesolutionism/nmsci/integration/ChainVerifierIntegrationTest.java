package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.verifier.ChainVerificationResult;
import com.cooperativesolutionism.nmsci.verifier.ChainVerifier;
import com.cooperativesolutionism.nmsci.verifier.DatBlockReader;
import com.cooperativesolutionism.nmsci.verifier.ParsedBlock;
import com.cooperativesolutionism.nmsci.verifier.VerifierOptions;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端集成验证：经真实 REST 入账全部消息类型 → 真实 generateBlock 落盘 → 文件读回并独立核验，
 * 同时验证 {@code GET /verify/chain} 端点。是对验证器与生产生成路径一致性的最终权威检查。
 */
class ChainVerifierIntegrationTest extends NmsciIntegrationTestBase {

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();
    private final ChainVerifier verifier = new ChainVerifier();

    @Resource
    private BlockChainService blockChainService;

    @Resource
    private BlockInfoRepository blockInfoRepository;

    private static final Path DAT_DIR = Path.of("target", "nmsci-test-files", "dat");

    @Test
    void verifiesRealChainProducedByFullIngestionPipeline() throws Exception {
        cleanDatDirectory();

        BlockInfo seededBlock = blockInfoRepository.findTopByOrderByHeightDesc();
        byte[] anchorPreviousHash = seededBlock.getId();

        UUID registerId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        UUID empowerId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000002");
        UUID recordId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003");
        UUID mountId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000004");

        submit("/flow-node-registrations",
                builder.flowNodeRegister(registerId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS));
        submit("/central-pubkey-empowerments",
                builder.centralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL));
        submit("/transaction-records",
                builder.transactionRecord(recordId, 500L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A,
                        TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS));
        submit("/transaction-mounts",
                builder.transactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A,
                        TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS));

        blockChainService.generateBlock();

        // 程序化文件核验（带锚点：.dat 首块为高度1，创世仅在库中播种）
        List<ParsedBlock> blocks = DatBlockReader.readDirectory(DAT_DIR);
        VerifierOptions options = VerifierOptions.builder()
                .startingPreviousHash(anchorPreviousHash)
                .expectedCentralPubkey(TestKeyPairs.CENTRAL.pubkey())
                .expectedSourceHashHex("0000000000000000000000000000000000000000000000000000000000000000")
                .includeStatefulReplay(true)
                .build();
        ChainVerificationResult result = verifier.verify(blocks, options);
        assertTrue(result.ok(), () -> "真实链文件核验应通过，但有失败:\n" + result.render());
        assertTrue(result.totalBlocks() >= 1);
        assertTrue(result.totalMessages() >= 4);

        // 端点核验（内部完整性，含有状态回放）
        mockMvc.perform(get("/verify/chain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    private void submit(String path, byte[] body) throws Exception {
        mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200));
    }

    private static void cleanDatDirectory() throws IOException {
        if (!Files.exists(DAT_DIR)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(DAT_DIR)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException("清理测试 dat 目录失败: " + path, e);
                }
            });
        }
    }
}
