package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProtocolErrorIntegrationTest extends NmsciIntegrationTestBase {

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();

    @Test
    void rejectsInvalidRegisterMessageLength() throws Exception {
        byte[] valid = builder.flowNodeRegister(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                TestKeyPairs.FLOW_NODE_A,
                REGISTER_DIFFICULTY_NBITS
        );

        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(Arrays.copyOf(valid, valid.length - 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("Validation failure")));
    }

    @Test
    void rejectsWrongMessageType() throws Exception {
        byte[] message = builder.flowNodeRegister(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                TestKeyPairs.FLOW_NODE_A,
                REGISTER_DIFFICULTY_NBITS
        );

        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.withMsgType(message, (short) 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("信息类型错误")));
    }

    @Test
    void rejectsDuplicateFlowNodeRegistration() throws Exception {
        UUID firstId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID secondId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(firstId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(secondId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("已被注册")));
    }

    @Test
    void rejectsTransactionRecordFromUnempoweredFlowNode() throws Exception {
        UUID flowNodeId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID recordId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/transaction-record-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.transactionRecord(recordId, 100L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("未授权")));
    }

    @Test
    void rejectsBrokenTransactionSignature() throws Exception {
        UUID flowNodeId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID empowerId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID recordId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        mockMvc.perform(post("/flow-node-register-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/central-pubkey-empower-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.centralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        byte[] valid = builder.transactionRecord(recordId, 100L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS);
        mockMvc.perform(post("/transaction-record-msg/send")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.withBrokenSignature(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("签名")));
    }
}
