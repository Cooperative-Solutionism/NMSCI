package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsumeChainRepositoryNodeFilterTest extends NmsciIntegrationTestBase {

    private static final Pageable PAGEABLE = PageRequest.of(
            0,
            20,
            Sort.by(Sort.Order.asc("tailMountTimestamp"), Sort.Order.asc("id"))
    );

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByNodeFilterMatchesStartEndNodeAndOptionalLoopFilter() {
        UUID start = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID end = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID edgeOnly = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID other = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID record = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID mount = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID startChain = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID endChain = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID edgeChain = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID loopChain = UUID.fromString("00000000-0000-0000-0000-000000000004");

        insertFlowNode(start, 1);
        insertFlowNode(end, 2);
        insertFlowNode(edgeOnly, 3);
        insertFlowNode(other, 4);
        insertTransactionRecord(record, 20);
        insertTransactionMount(mount, record, 21);
        insertChain(startChain, start, other, false, 10L);
        insertChain(endChain, other, end, false, 20L);
        insertChain(edgeChain, other, other, false, 30L);
        insertChain(loopChain, start, end, true, 40L);
        insertEdge(
                UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                edgeOnly,
                other,
                edgeChain,
                record,
                mount,
                false,
                30L
        );

        assertIds(
                consumeChainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, node(start), null, PAGEABLE),
                startChain,
                loopChain
        );
        assertIds(
                consumeChainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, node(start), false, PAGEABLE),
                startChain
        );
        assertIds(
                consumeChainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, node(start), true, PAGEABLE),
                loopChain
        );
        assertIds(
                consumeChainRepository.findByNodeFilter(ConsumeChainNodeFilter.END, node(end), null, PAGEABLE),
                endChain,
                loopChain
        );
        assertIds(
                consumeChainRepository.findByNodeFilter(ConsumeChainNodeFilter.NODE, node(edgeOnly), null, PAGEABLE),
                edgeChain
        );
    }

    private void insertFlowNode(UUID id, int seed) {
        jdbcTemplate.update("""
                insert into flow_node_register_msgs
                    (id, msg_type, register_difficulty_target, nonce, flow_node_pubkey,
                     flow_node_signature, raw_bytes, txid)
                values (?, 0, ?, ?, ?, ?, ?, ?)
                """,
                id,
                REGISTER_DIFFICULTY_NBITS,
                seed,
                bytes(33, seed),
                bytes(64, seed),
                bytes(123, seed),
                bytes(32, seed)
        );
    }

    private void insertTransactionRecord(UUID id, int seed) {
        jdbcTemplate.update("""
                insert into transaction_record_msgs
                    (id, msg_type, amount, currency_type, transaction_difficulty_target, nonce,
                     consume_node_pubkey, flow_node_pubkey, central_pubkey, consume_node_signature,
                     flow_node_signature, confirm_timestamp, central_signature, raw_bytes, txid)
                values (?, 4, 100, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                TRANSACTION_DIFFICULTY_NBITS,
                seed,
                bytes(33, seed),
                bytes(33, seed + 1),
                bytes(33, seed + 2),
                bytes(64, seed),
                bytes(64, seed + 1),
                (long) seed,
                bytes(64, seed + 2),
                bytes(335, seed),
                bytes(32, seed)
        );
    }

    private void insertTransactionMount(UUID id, UUID recordId, int seed) {
        jdbcTemplate.update("""
                insert into transaction_mount_msgs
                    (id, msg_type, mounted_transaction_record_id, transaction_difficulty_target, nonce,
                     consume_node_pubkey, flow_node_pubkey, central_pubkey, consume_node_signature,
                     flow_node_signature, confirm_timestamp, central_signature, raw_bytes, txid)
                values (?, 5, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                recordId,
                TRANSACTION_DIFFICULTY_NBITS,
                seed,
                bytes(33, seed),
                bytes(33, seed + 1),
                bytes(33, seed + 2),
                bytes(64, seed),
                bytes(64, seed + 1),
                (long) seed,
                bytes(64, seed + 2),
                bytes(341, seed),
                bytes(32, seed)
        );
    }

    private void insertChain(UUID id, UUID start, UUID end, boolean isLoop, long tailMountTimestamp) {
        jdbcTemplate.update("""
                insert into consume_chains
                    (id, start, "end", amount, currency_type, is_loop, tail_mount_timestamp)
                values (?, ?, ?, 100, 1, ?, ?)
                """,
                id,
                start,
                end,
                isLoop,
                tailMountTimestamp
        );
    }

    private void insertEdge(
            UUID id,
            UUID source,
            UUID target,
            UUID chain,
            UUID record,
            UUID mount,
            boolean isLoop,
            long timestamp
    ) {
        jdbcTemplate.update("""
                insert into consume_chain_edges
                    (id, source, target, amount, currency_type, chain, related_transaction_record,
                     related_transaction_mount, related_transaction_mount_timestamp, is_loop)
                values (?, ?, ?, 100, 1, ?, ?, ?, ?, ?)
                """,
                id,
                source,
                target,
                chain,
                record,
                mount,
                timestamp,
                isLoop
        );
    }

    private FlowNodeRegisterMsg node(UUID id) {
        FlowNodeRegisterMsg node = new FlowNodeRegisterMsg();
        node.setId(id);
        return node;
    }

    private void assertIds(Slice<ConsumeChain> actual, UUID... expected) {
        List<UUID> actualIds = actual.getContent().stream()
                .map(ConsumeChain::getId)
                .toList();

        assertEquals(List.of(expected), actualIds);
    }

    private byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        value[0] = (byte) seed;
        return value;
    }
}
