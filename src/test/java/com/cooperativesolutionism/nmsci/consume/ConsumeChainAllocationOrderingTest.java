package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证挂载分配的开放链选择排序：优先 start==target（延伸后即成环），其次按 tail_mount_timestamp 升序。
 * 由于 {@code lockOpenChainsForAllocation} 带 FOR UPDATE，必须在事务中执行，这里用 TransactionTemplate 包裹；
 * 插入则沿用 {@code ConsumeChainRepositoryNodeFilterTest} 的自动提交模式（每个用例由父类 @BeforeEach 重置数据库）。
 */
class ConsumeChainAllocationOrderingTest extends NmsciIntegrationTestBase {

    private static final short CURRENCY_TYPE = 1;

    private static final UUID SOURCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID UNRELATED = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final UUID CHAIN_OLD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHAIN_MATCH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Resource
    private ConsumeChainRepository consumeChainRepository;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PlatformTransactionManager transactionManager;

    @Test
    void startEqualsTargetChainIsSelectedBeforeOlderNonMatchingChainWhenBothCovered() {
        seedSourceTargetAndOtherNodes();
        // chainOld：更早挂载，但 start != target；chainMatch：更晚挂载，但 start == target。
        insertChain(CHAIN_OLD, OTHER, SOURCE, false, 10L);
        insertChain(CHAIN_MATCH, TARGET, SOURCE, false, 20L);

        // amount=150 > 单链 100，两条链都进入最小前缀；start==target 的链应排在最前（尽管更晚）。
        List<ConsumeChain> selected = lockForAllocation(SOURCE, 150L, TARGET);

        assertEquals(List.of(CHAIN_MATCH, CHAIN_OLD), ids(selected));
    }

    @Test
    void startEqualsTargetChainIsSelectedWhenMinimalPrefixHoldsOnlyOneChain() {
        seedSourceTargetAndOtherNodes();
        insertChain(CHAIN_OLD, OTHER, SOURCE, false, 10L);
        insertChain(CHAIN_MATCH, TARGET, SOURCE, false, 20L);

        // amount=100 仅够一条进入最小前缀；只有当窗口子查询也以 start==target 为首键时，返回的才是 chainMatch。
        List<ConsumeChain> selected = lockForAllocation(SOURCE, 100L, TARGET);

        assertEquals(List.of(CHAIN_MATCH), ids(selected));
    }

    @Test
    void fallsBackToTimeOrderWhenNoChainStartEqualsTarget() {
        seedSourceTargetAndOtherNodes();
        insertFlowNode(UNRELATED, 9);
        insertChain(CHAIN_OLD, OTHER, SOURCE, false, 10L);
        insertChain(CHAIN_MATCH, TARGET, SOURCE, false, 20L);

        // 目标节点不是任何链的 start，优先键全为 false，退化为纯时间升序。
        List<ConsumeChain> selected = lockForAllocation(SOURCE, 150L, UNRELATED);

        assertEquals(List.of(CHAIN_OLD, CHAIN_MATCH), ids(selected));
    }

    private List<ConsumeChain> lockForAllocation(UUID endId, long amount, UUID targetId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status ->
                consumeChainRepository.lockOpenChainsForAllocation(endId, CURRENCY_TYPE, amount, targetId)
        );
    }

    private void seedSourceTargetAndOtherNodes() {
        insertFlowNode(SOURCE, 1);
        insertFlowNode(TARGET, 2);
        insertFlowNode(OTHER, 3);
    }

    private List<UUID> ids(List<ConsumeChain> chains) {
        return chains.stream().map(ConsumeChain::getId).toList();
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

    private byte[] bytes(int length, int seed) {
        byte[] value = new byte[length];
        value[0] = (byte) seed;
        return value;
    }
}
