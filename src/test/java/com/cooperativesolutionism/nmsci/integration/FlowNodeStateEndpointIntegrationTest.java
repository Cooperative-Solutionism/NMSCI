package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FlowNodeStateEndpointIntegrationTest extends NmsciIntegrationTestBase {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void returnsAggregateFlowNodeStateFromDatabase() throws Exception {
        assertState(TestKeyPairs.FLOW_NODE_B.pubkey(), false, false, false, false);

        insertFlowNodeRegister(TestKeyPairs.FLOW_NODE_A.pubkey());
        insertCentralPubkeyEmpower(TestKeyPairs.FLOW_NODE_A.pubkey(), TestKeyPairs.FLOW_NODE_B.pubkey());
        insertFlowNodeLocked(TestKeyPairs.FLOW_NODE_A.pubkey());
        assertState(TestKeyPairs.FLOW_NODE_A.pubkey(), true, true, true, false);

        insertFlowNodeRegister(TestKeyPairs.FLOW_NODE_B.pubkey());
        insertCentralPubkeyEmpower(TestKeyPairs.FLOW_NODE_B.pubkey(), TestKeyPairs.CENTRAL.pubkey());
        assertState(TestKeyPairs.FLOW_NODE_B.pubkey(), true, true, false, true);
    }

    private void assertState(
            byte[] flowNodePubkey,
            boolean registered,
            boolean authorized,
            boolean locked,
            boolean currentCentralPubkeyAuthorized
    ) throws Exception {
        mockMvc.perform(get("/flow-node/state")
                        .param("flowNodePubkey", ByteArrayUtil.bytesToHex(flowNodePubkey)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.registered").value(registered))
                .andExpect(jsonPath("$.data.authorized").value(authorized))
                .andExpect(jsonPath("$.data.locked").value(locked))
                .andExpect(jsonPath("$.data.currentCentralPubkeyAuthorized").value(currentCentralPubkeyAuthorized));
    }

    private void insertFlowNodeRegister(byte[] flowNodePubkey) {
        jdbcTemplate.update("""
                        insert into flow_node_register_msgs
                            (id, msg_type, register_difficulty_target, nonce, flow_node_pubkey, flow_node_signature, raw_bytes, txid)
                        values (?, 0, ?, 0, ?, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                REGISTER_DIFFICULTY_NBITS,
                flowNodePubkey,
                new byte[64],
                new byte[123],
                txidFrom(flowNodePubkey)
        );
    }

    private void insertCentralPubkeyEmpower(byte[] flowNodePubkey, byte[] centralPubkey) {
        jdbcTemplate.update("""
                        insert into central_pubkey_empower_msgs
                            (id, msg_type, flow_node_pubkey, central_pubkey, flow_node_signature, confirm_timestamp, central_signature, raw_bytes, txid)
                        values (?, 1, ?, ?, ?, 1, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                flowNodePubkey,
                centralPubkey,
                new byte[64],
                new byte[64],
                new byte[220],
                txidFrom(flowNodePubkey)
        );
    }

    private void insertFlowNodeLocked(byte[] flowNodePubkey) {
        jdbcTemplate.update("""
                        insert into flow_node_locked_msgs
                            (id, msg_type, flow_node_pubkey, central_pubkey, flow_node_signature, confirm_timestamp, central_signature, raw_bytes, txid)
                        values (?, 3, ?, ?, ?, 1, ?, ?, ?)
                        """,
                UUID.randomUUID(),
                flowNodePubkey,
                TestKeyPairs.CENTRAL.pubkey(),
                new byte[64],
                new byte[64],
                new byte[220],
                txidFrom(flowNodePubkey)
        );
    }

    private byte[] txidFrom(byte[] seed) {
        byte[] txid = new byte[32];
        System.arraycopy(seed, 0, txid, 0, Math.min(seed.length, txid.length));
        return txid;
    }
}
