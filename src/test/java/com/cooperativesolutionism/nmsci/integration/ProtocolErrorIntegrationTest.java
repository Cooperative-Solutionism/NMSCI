package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(Arrays.copyOf(valid, valid.length - 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数非法"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsWrongMessageType() throws Exception {
        byte[] message = builder.flowNodeRegister(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                TestKeyPairs.FLOW_NODE_A,
                REGISTER_DIFFICULTY_NBITS
        );

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.withMsgType(message, (short) 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("信息类型错误")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsDuplicateFlowNodeRegistration() throws Exception {
        UUID firstId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID secondId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(firstId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(secondId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(containsString("已被注册")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsTransactionRecordFromUnempoweredFlowNode() throws Exception {
        UUID flowNodeId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID recordId = UUID.fromString("66666666-6666-6666-6666-666666666666");

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/transaction-records")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.transactionRecord(recordId, 100L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("未授权")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsBrokenTransactionSignature() throws Exception {
        UUID flowNodeId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID empowerId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID recordId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/central-pubkey-empowerments")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.centralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        byte[] valid = builder.transactionRecord(recordId, 100L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL, TRANSACTION_DIFFICULTY_NBITS);
        mockMvc.perform(post("/transaction-records")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.withBrokenSignature(valid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("签名")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsReturningFlowRateLookupWhenTargetPubkeyIsNotRegistered() throws Exception {
        mockMvc.perform(get("/returning-flow-rates")
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_B.pubkey())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("流转节点公钥")))
                .andExpect(jsonPath("$.message").value(containsString("不存在")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsReturningFlowRateLookupWhenSourcePubkeyIsNotRegistered() throws Exception {
        UUID targetId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(targetId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/returning-flow-rates")
                        .param("sourcePubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_B.pubkey()))
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("源流转节点公钥")))
                .andExpect(jsonPath("$.message").value(containsString("不存在")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsConsumeChainEdgesWhenPageSizeExceedsLimit() throws Exception {
        mockMvc.perform(get("/consume-chains/edges")
                        .param("targetId", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").toString())
                        .param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("分页大小不能超过200")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsConsumeChainEdgesWhenTargetIsMissing() throws Exception {
        mockMvc.perform(get("/consume-chains/edges"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("targetId 不能为空")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsConsumeChainEdgesWhenIdAndPubkeyAreMixed() throws Exception {
        mockMvc.perform(get("/consume-chains/edges")
                        .param("targetId", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb").toString())
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("id 与 pubkey 查询参数不能混用")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsConsumeChainEdgesWhenTargetPubkeyIsNotRegistered() throws Exception {
        mockMvc.perform(get("/consume-chains/edges")
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_B.pubkey())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("目标流转节点公钥")))
                .andExpect(jsonPath("$.message").value(containsString("不存在")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsConsumeChainEdgesWhenSourcePubkeyIsNotRegistered() throws Exception {
        UUID targetId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        mockMvc.perform(post("/flow-node-registrations")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(builder.flowNodeRegister(targetId, TestKeyPairs.FLOW_NODE_A, REGISTER_DIFFICULTY_NBITS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/consume-chains/edges")
                        .param("sourcePubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_B.pubkey()))
                        .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("源流转节点公钥")))
                .andExpect(jsonPath("$.message").value(containsString("不存在")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsConsumeChainEdgesWhenTargetPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/consume-chains/edges")
                        .param("targetPubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("目标流转节点公钥不能为空或长度不为33字节")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsFlowNodeStateLookupWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/flow-nodes/{flowNodePubkey}", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("流转节点公钥不能为空或长度不为33字节")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsFlowNodeLockStatusWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/flow-node-locks/status")
                        .param("flowNodePubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("流转节点公钥不能为空或长度不为33字节")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsCentralPubkeyLockStatusWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/central-pubkey-locks/status")
                        .param("centralPubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("中心公钥不能为空或长度不为33字节")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void returnsUnlockedStateWhenFlowNodeLockedLookupByPubkeyIsMissing() throws Exception {
        mockMvc.perform(get("/flow-node-locks/status")
                        .param("flowNodePubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.lockedMsg").value(nullValue()));
    }

    @Test
    void returnsUnlockedStateWhenCentralPubkeyLockedLookupByPubkeyIsMissing() throws Exception {
        mockMvc.perform(get("/central-pubkey-locks/status")
                        .param("centralPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.CENTRAL.pubkey())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.locked").value(false))
                .andExpect(jsonPath("$.data.lockedMsg").value(nullValue()));
    }

    @Test
    void returnsEmptySliceWhenTransactionMountLookupByMountedTransactionRecordIdMisses() throws Exception {
        UUID mountedRecordId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        mockMvc.perform(get("/transaction-mounts")
                        .param("mountedTransactionRecordId", mountedRecordId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.numberOfElements").value(0));
    }

    @Test
    void rejectsFlowNodeRegistrationSearchWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/flow-node-registrations")
                        .param("flowNodePubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("公钥长度错误")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsCentralPubkeyEmpowermentSearchWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/central-pubkey-empowerments")
                        .param("flowNodePubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("公钥长度错误")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsTransactionRecordSearchWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/transaction-records")
                        .param("flowNodePubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("公钥长度错误")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsTransactionMountSearchWhenPubkeyHasWrongLength() throws Exception {
        mockMvc.perform(get("/transaction-mounts")
                        .param("consumeNodePubkey", "00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("公钥长度错误")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsMissingBlockLookupByHeight() throws Exception {
        mockMvc.perform(get("/blocks/{height}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value(containsString("不存在")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void rejectsBlockLookupWhenHashIsMalformed() throws Exception {
        mockMvc.perform(get("/blocks")
                        .param("hash", "zz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("十六进制字符串包含非法字符")))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
