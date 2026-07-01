package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import com.cooperativesolutionism.nmsci.support.NmsciIntegrationTestBase;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPair;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class ConsumeChainAllocationConcurrencyIntegrationTest extends NmsciIntegrationTestBase {

    private static final TestKeyPair SOURCE_A = TestKeyPairs.deriveByIndex(201);
    private static final TestKeyPair SHARED_SOURCE_B = TestKeyPairs.deriveByIndex(202);
    private static final TestKeyPair TARGET_C = TestKeyPairs.deriveByIndex(203);
    private static final TestKeyPair CONSUME_NODE = TestKeyPairs.CONSUME_NODE_A;

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();
    private final FlowNodeRegisterMsgConverter flowNodeRegisterConverter = new FlowNodeRegisterMsgConverter();
    private final CentralPubkeyEmpowerMsgConverter centralPubkeyEmpowerConverter = new CentralPubkeyEmpowerMsgConverter();
    private final TransactionRecordMsgConverter transactionRecordConverter = new TransactionRecordMsgConverter();
    private final TransactionMountMsgConverter transactionMountConverter = new TransactionMountMsgConverter();

    @Resource
    private FlowNodeRegisterMsgService flowNodeRegisterMsgService;

    @Resource
    private CentralPubkeyEmpowerMsgService centralPubkeyEmpowerMsgService;

    @Resource
    private TransactionRecordMsgService transactionRecordMsgService;

    @Resource
    private TransactionMountMsgService transactionMountMsgService;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private DataSource dataSource;

    @Test
    void concurrentMountsSerializeSharedOpenChainAllocation() throws Throwable {
        UUID sourceAId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID sharedSourceBId = UUID.fromString("10000000-0000-0000-0000-000000000002");
        UUID targetCId = UUID.fromString("10000000-0000-0000-0000-000000000003");

        registerAndAuthorize(sourceAId, UUID.fromString("20000000-0000-0000-0000-000000000001"), SOURCE_A);
        registerAndAuthorize(sharedSourceBId, UUID.fromString("20000000-0000-0000-0000-000000000002"), SHARED_SOURCE_B);
        registerAndAuthorize(targetCId, UUID.fromString("20000000-0000-0000-0000-000000000003"), TARGET_C);

        UUID seedRecordId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID seedMountId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        saveRecord(seedRecordId, 1000L, SHARED_SOURCE_B);
        saveMount(seedMountId, seedRecordId, SOURCE_A);

        assertEquals(1L, chainCountEndingAt(sharedSourceBId));
        assertEquals(1000L, chainAmountEndingAt(sharedSourceBId));
        assertEquals(1000L, edgeAmountForMount(seedMountId));

        UUID firstRecordId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID secondRecordId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID firstMountId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        UUID secondMountId = UUID.fromString("40000000-0000-0000-0000-000000000003");

        saveRecord(firstRecordId, 700L, TARGET_C);
        saveRecord(secondRecordId, 700L, TARGET_C);

        try (Connection lockConnection = dataSource.getConnection()) {
            boolean[] lockReleased = {false};
            lockConnection.setAutoCommit(false);
            assertEquals(1, lockOpenChainEndingAt(lockConnection, sharedSourceBId));

            runConcurrently(
                    mount(firstMountId, firstRecordId, SHARED_SOURCE_B),
                    mount(secondMountId, secondRecordId, SHARED_SOURCE_B),
                    new ConcurrentReleaseGate() {
                        @Override
                        public void releaseAfterWorkersBlock() throws Exception {
                            awaitBlockedAllocationWorkers();
                            lockConnection.commit();
                            lockReleased[0] = true;
                        }

                        @Override
                        public void releaseAfterFailure(Throwable failure) {
                            if (!lockReleased[0]) {
                                rollbackPreLock(lockConnection, failure);
                                lockReleased[0] = true;
                            }
                        }
                    }
            );
        }

        assertTrue(transactionMountMsgRepository.existsById(firstMountId));
        assertTrue(transactionMountMsgRepository.existsById(secondMountId));

        assertEquals(1000L, edgeAmountForMount(seedMountId));
        assertEquals(700L, edgeAmountForMount(firstMountId));
        assertEquals(700L, edgeAmountForMount(secondMountId));
        assertEquals(0L, nonPositiveChainCount());
        assertEquals(0L, chainCountEndingAt(sharedSourceBId), "the original B-ended open chain must not remain after two 700 mounts");
        assertEquals(1400L, chainAmountEndingAt(targetCId));
        assertEquals(0L, oversizedConcurrentMountEdgeCount(firstMountId, secondMountId));
    }

    private void registerAndAuthorize(UUID registerId, UUID empowerId, TestKeyPair flowNode) {
        flowNodeRegisterMsgService.saveFlowNodeRegisterMsg(flowNodeRegister(registerId, flowNode));
        centralPubkeyEmpowerMsgService.saveCentralPubkeyEmpowerMsg(centralPubkeyEmpower(empowerId, flowNode));
    }

    private FlowNodeRegisterMsg flowNodeRegister(UUID id, TestKeyPair flowNode) {
        return flowNodeRegisterConverter.fromByteArray(builder.flowNodeRegister(
                id,
                flowNode,
                REGISTER_DIFFICULTY_NBITS
        ));
    }

    private CentralPubkeyEmpowerMsg centralPubkeyEmpower(UUID id, TestKeyPair flowNode) {
        return centralPubkeyEmpowerConverter.fromByteArray(builder.centralPubkeyEmpower(
                id,
                flowNode,
                TestKeyPairs.CENTRAL
        ));
    }

    private TransactionRecordMsg saveRecord(UUID id, long amount, TestKeyPair targetFlowNode) {
        TransactionRecordMsg record = transactionRecordConverter.fromByteArray(builder.transactionRecord(
                id,
                amount,
                CONSUME_NODE,
                targetFlowNode,
                TestKeyPairs.CENTRAL,
                TRANSACTION_DIFFICULTY_NBITS
        ));
        return transactionRecordMsgService.saveTransactionRecordMsg(record);
    }

    private TransactionMountMsg saveMount(UUID id, UUID recordId, TestKeyPair sourceFlowNode) {
        return transactionMountMsgService.saveTransactionMountMsg(mount(id, recordId, sourceFlowNode));
    }

    private TransactionMountMsg mount(UUID id, UUID recordId, TestKeyPair sourceFlowNode) {
        return transactionMountConverter.fromByteArray(builder.transactionMount(
                id,
                recordId,
                CONSUME_NODE,
                sourceFlowNode,
                TestKeyPairs.CENTRAL,
                TRANSACTION_DIFFICULTY_NBITS
        ));
    }

    private void runConcurrently(
            TransactionMountMsg firstMount,
            TransactionMountMsg secondMount,
            ConcurrentReleaseGate releaseGate
    ) throws Throwable {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        boolean success = false;

        try {
            executorService.execute(() -> saveAfterStart(firstMount, ready, start, done, failures));
            executorService.execute(() -> saveAfterStart(secondMount, ready, start, done, failures));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "concurrent workers did not become ready");
            start.countDown();
            releaseGate.releaseAfterWorkersBlock();
            assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent mount saves did not finish");
            assertNoWorkerFailures(failures);
            success = true;
        } catch (Throwable throwable) {
            releaseGate.releaseAfterFailure(throwable);
            throw throwable;
        } finally {
            start.countDown();
            executorService.shutdownNow();
            if (success) {
                assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS), "concurrent executor did not stop");
            } else {
                awaitTerminationAfterFailure(executorService);
            }
        }
    }

    private void awaitTerminationAfterFailure(ExecutorService executorService) {
        try {
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ConcurrentReleaseGate {
        void releaseAfterWorkersBlock() throws Exception;

        default void releaseAfterFailure(Throwable failure) {
        }
    }

    private void rollbackPreLock(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (Exception rollbackException) {
            failure.addSuppressed(rollbackException);
        }
    }

    private void saveAfterStart(
            TransactionMountMsg mount,
            CountDownLatch ready,
            CountDownLatch start,
            CountDownLatch done,
            List<Throwable> failures
    ) {
        ready.countDown();
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("concurrent worker was not released");
            }
            transactionMountMsgService.saveTransactionMountMsg(mount);
        } catch (Throwable throwable) {
            failures.add(throwable);
        } finally {
            done.countDown();
        }
    }

    private static void assertNoWorkerFailures(List<Throwable> failures) {
        if (failures.isEmpty()) {
            return;
        }
        AssertionError assertionError = new AssertionError("concurrent mount worker failed");
        failures.forEach(assertionError::addSuppressed);
        throw assertionError;
    }

    private long chainCountEndingAt(UUID flowNodeId) {
        return queryLong("select count(*) from consume_chains where \"end\" = ?", flowNodeId);
    }

    private int lockOpenChainEndingAt(Connection connection, UUID flowNodeId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select id from consume_chains where \"end\" = ? and is_loop = false for update"
        )) {
            statement.setObject(1, flowNodeId);
            try (ResultSet resultSet = statement.executeQuery()) {
                int rows = 0;
                while (resultSet.next()) {
                    rows++;
                }
                return rows;
            }
        }
    }

    private void awaitBlockedAllocationWorkers() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            long blockedWorkers = queryLong(
                    """
                    select count(*)
                    from pg_stat_activity
                    where datname = current_database()
                        and pid <> pg_backend_pid()
                        and wait_event_type = 'Lock'
                        and lower(query) like '%consume_chains%'
                    """
            );
            if (blockedWorkers >= 2) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("two concurrent workers did not both block on the consume_chains allocation lock");
    }

    private long chainAmountEndingAt(UUID flowNodeId) {
        return queryLong("select coalesce(sum(amount), 0) from consume_chains where \"end\" = ?", flowNodeId);
    }

    private long edgeAmountForMount(UUID mountId) {
        return queryLong("select coalesce(sum(amount), 0) from consume_chain_edges where related_transaction_mount = ?", mountId);
    }

    private long nonPositiveChainCount() {
        return queryLong("select count(*) from consume_chains where amount <= 0");
    }

    private long oversizedConcurrentMountEdgeCount(UUID firstMountId, UUID secondMountId) {
        return queryLong(
                """
                select count(*)
                from consume_chain_edges
                where related_transaction_mount in (?, ?)
                    and amount >= 1000
                """,
                firstMountId,
                secondMountId
        );
    }

    private long queryLong(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }
}
