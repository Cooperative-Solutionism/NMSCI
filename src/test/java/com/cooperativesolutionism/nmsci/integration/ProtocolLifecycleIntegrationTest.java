package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPair;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProtocolLifecycleIntegrationTest extends NmsciIntegrationTestBase {

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private ConsumeChainEdgeRepository consumeChainEdgeRepository;

    @Test
    void databaseIsResetWithLatestBlock() {
        var newestBlock = blockInfoRepository.findTopByOrderByHeightDesc();

        assertNotNull(newestBlock);
        assertEquals(0L, newestBlock.getHeight());
        assertEquals(REGISTER_DIFFICULTY_NBITS, newestBlock.getRegisterDifficultyTarget());
        assertEquals(TRANSACTION_DIFFICULTY_NBITS, newestBlock.getTransactionDifficultyTarget());
    }

    @Test
    void savesFlowNodeRegisterMessage() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);

        var saved = flowNodeRegisterMsgRepository.findById(flowNodeId).orElseThrow();
        assertArrayEquals(TestKeyPairs.FLOW_NODE_A.pubkey(), saved.getFlowNodePubkey());
        assertEquals(123, saved.getRawBytes().length);
        assertEquals(32, saved.getTxid().length);
    }

    @Test
    void savesEmpoweredTransactionRecordAndMount() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        var record = transactionRecordMsgRepository.findById(recordId).orElseThrow();
        var mount = transactionMountMsgRepository.findById(mountId).orElseThrow();

        assertEquals(1200L, record.getAmount());
        assertEquals(335, record.getRawBytes().length);
        assertEquals(recordId, mount.getMountedTransactionRecordId());
        assertEquals(341, mount.getRawBytes().length);
        assertEquals(1, consumeChainRepository.count());
        assertEquals(1, consumeChainEdgeRepository.count());
    }

    @Test
    void queryConsumeChainByMountedTransactionUsesChainSort() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/consume-chain/by-mounted-transaction")
                        .param("relatedTransactionMount", mountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.numberOfElements").value(1));
    }

    @Test
    void returningFlowRateIsAggregatedByDatabase() throws Exception {
        UUID flowNodeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID mountId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
        sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
        sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
        sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/returning-flow-rate/by-id")
                        .param("sourceId", flowNodeId.toString())
                        .param("targetId", flowNodeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.returningFlowRate").value(1.0))
                .andExpect(jsonPath("$.data.loopedAmount").value(1200.0))
                .andExpect(jsonPath("$.data.unloopedAmount").value(0.0));

        mockMvc.perform(get("/returning-flow-rate/by-id")
                        .param("targetId", flowNodeId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.targetTotalLoopedAmount").value(1200.0))
                .andExpect(jsonPath("$.data.targetTotalUnloopedAmount").value(0.0));
    }

    @Test
    void queryByFlowNodePubkeyReturnsSavedRegisterMessage() throws Exception {
        UUID flowNodeId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);

        mockMvc.perform(get("/flow-node-register-msg/flow-node-pubkey/{pubkey}", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(flowNodeId.toString()));
    }

    private void sendFlowNodeRegister(UUID id, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(id, flowNode, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void sendCentralPubkeyEmpower(UUID id, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/central-pubkey-empower-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.centralPubkeyEmpower(id, flowNode, TestKeyPairs.CENTRAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertTrue(centralPubkeyEmpowerMsgRepository.existsById(id));
    }

    private void sendTransactionRecord(UUID id, long amount, TestKeyPair consumeNode, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/transaction-record-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.transactionRecord(id, amount, consumeNode, flowNode, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void sendTransactionMount(UUID id, UUID recordId, TestKeyPair consumeNode, TestKeyPair flowNode) throws Exception {
        mockMvc.perform(post("/transaction-mount-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.transactionMount(id, recordId, consumeNode, flowNode, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
