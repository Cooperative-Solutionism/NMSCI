# Consume Chain Concurrency Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real two-thread PostgreSQL-backed integration test proving concurrent transaction mounts do not duplicate-consume the same open consume chain.

**Architecture:** Add one focused Failsafe integration test at the service boundary so both worker threads enter real Spring `@Transactional` proxies and real PostgreSQL locks. Keep production code unchanged if the test passes; if it exposes a race, apply the smallest database-locking fix in `ConsumeChainRepository`.

**Tech Stack:** Java 21, JUnit 5, Spring Boot Test, Maven Failsafe, PostgreSQL Testcontainers, `ExecutorService`, `CountDownLatch`, `JdbcTemplate`.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-16-consume-chain-concurrency-test-design.md`.

In scope:
- A real two-thread integration test for concurrent `TransactionMountMsgService.saveTransactionMountMsg` calls.
- Test data that uses different mounted transaction records so `findByIdForUpdate` does not serialize the scenario before consume-chain allocation.
- Assertions over final persisted consume-chain and consume-chain-edge invariants.
- A minimal repository SQL fix only if the new test exposes a race.
- Audit status update after verification.

Out of scope:
- HTTP endpoint concurrency testing.
- JVM global locks or broad serialization.
- Rewriting `ConsumeChainAllocator`.
- Entity `@Version`, `equals/hashCode`, or other Low/Info concurrency hardening.
- API error-contract, rawBytes, or service-template refactors.

## File Structure

- Create `src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java`: focused Failsafe integration test that builds legal protocol messages, saves setup data through services, races two mount saves, and asserts final database invariants through `JdbcTemplate`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java` only if the new integration test fails with a real stale-open-chain race: replace the native lock query with a CTE that locks all matching open chains before computing the window prefix.
- Modify `docs/code-quality-audit-status.md`: mark the real two-thread concurrency test item fixed, update verification counts, and adjust next-priority text.

---

## Task 1: Add Real Two-Thread Consume-Chain Concurrency Test

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java`

- [ ] **Step 1: Create the integration test class**

Create `src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java` with this full content:

```java
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

    @Test
    void concurrentMountsSerializeSharedOpenChainAllocation() throws Exception {
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

        UUID firstRecordId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        UUID secondRecordId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID firstMountId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        UUID secondMountId = UUID.fromString("40000000-0000-0000-0000-000000000003");

        saveRecord(firstRecordId, 700L, TARGET_C);
        saveRecord(secondRecordId, 700L, TARGET_C);

        runConcurrently(
                mount(firstMountId, firstRecordId, SHARED_SOURCE_B),
                mount(secondMountId, secondRecordId, SHARED_SOURCE_B)
        );

        assertTrue(transactionMountMsgRepository.existsById(firstMountId));
        assertTrue(transactionMountMsgRepository.existsById(secondMountId));

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

    private void runConcurrently(TransactionMountMsg firstMount, TransactionMountMsg secondMount) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            executorService.execute(() -> saveAfterStart(firstMount, ready, start, done, failures));
            executorService.execute(() -> saveAfterStart(secondMount, ready, start, done, failures));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "concurrent workers did not become ready");
            start.countDown();
            assertTrue(done.await(60, TimeUnit.SECONDS), "concurrent mount saves did not finish");
            assertNoWorkerFailures(failures);
        } finally {
            start.countDown();
            executorService.shutdownNow();
            assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS), "concurrent executor did not stop");
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
```

- [ ] **Step 2: Run the focused integration test**

Run:

```powershell
.\mvnw.cmd -Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest verify
```

Expected result:
- If current database locking is sufficient: PASS, `ConsumeChainAllocationConcurrencyIntegrationTest` reports 1 test, 0 failures, 0 errors, 0 skipped.
- If the audit concern is an active race: FAIL with one of these useful symptoms:
  - worker failure from a concurrent transaction exception,
  - `chainCountEndingAt(sharedSourceBId)` remains nonzero,
  - `chainAmountEndingAt(targetCId)` is not 1400,
  - a concurrent mount edge amount reaches 1000.

- [ ] **Step 3: Commit test-only change if focused test passes**

Run this only when Step 2 passes without production changes:

```powershell
git add src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "test: 增加消费链真实并发测试"
```

If Step 2 fails with a real race, do not commit yet. Continue to Task 2 and commit the test together with the minimal fix.

---

## Task 2: Minimal Lock Query Fix If the Concurrency Test Fails

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java`

Run this task only if Task 1 Step 2 fails with a real stale-open-chain race. Skip this task if Task 1 passes.

- [ ] **Step 1: Replace the native lock query with a lock-first CTE**

In `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`, replace the `@Query` on `lockOpenChainsForAllocation` with:

```java
    @Query(value = """
            with locked_open_chains as (
                select c.*
                from consume_chains c
                where c."end" = :endId
                    and c.currency_type = :currencyType
                    and c.is_loop = false
                order by c.tail_mount_timestamp, c.id
                for update
            ),
            selected_open_chains as (
                select loc.id,
                       coalesce(sum(loc.amount) over (
                           order by loc.tail_mount_timestamp, loc.id
                           rows between unbounded preceding and 1 preceding
                       ), 0) as prior_sum
                from locked_open_chains loc
            )
            select loc.*
            from locked_open_chains loc
            join selected_open_chains selected on selected.id = loc.id
            where selected.prior_sum < :amount
            order by loc.tail_mount_timestamp, loc.id
            """, nativeQuery = true)
```

This keeps locking scoped to open chains for one end node and currency, but ensures the rows are locked before the allocation window is computed.

- [ ] **Step 2: Run the focused integration test**

Run:

```powershell
.\mvnw.cmd -Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest verify
```

Expected: PASS with 1 test, 0 failures, 0 errors, 0 skipped.

- [ ] **Step 3: Run the existing repository optimization contract**

Run:

```powershell
.\mvnw.cmd -Dtest=ConsumeChainRepositoryOptimizationContractTest test
```

Expected: PASS. This ensures the repository method contract still exists and remains optimized around a single lock query shape.

- [ ] **Step 4: Commit test and minimal lock fix**

Run:

```powershell
git add src/test/java/com/cooperativesolutionism/nmsci/integration/ConsumeChainAllocationConcurrencyIntegrationTest.java src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "fix: 加固消费链并发分配锁"
```

---

## Task 3: Update Audit Status and Run Full Verification

**Files:**
- Modify: `docs/code-quality-audit-status.md`
- Test: focused Failsafe, full Surefire, full Failsafe

- [ ] **Step 1: Update the top scope and verification summary**

In `docs/code-quality-audit-status.md`, update the top `codex 修复范围` line to include:

```markdown
`docs/superpowers/specs/2026-06-16-consume-chain-concurrency-test-design.md` / `docs/superpowers/plans/2026-06-16-consume-chain-concurrency-test.md`（消费链真实两线程并发测试）
```

Update the top `验证手段` line after running the commands in Steps 3-5. Use the exact counts from command output.

- [ ] **Step 2: Mark the concurrency item fixed**

Move or rewrite this item from `## 3. 有意延后`:

```markdown
- **缺真实两线程并发测试**（Medium，Test）：双挂载 / 并发分配仅靠反射断言注解；建议补 `ExecutorService` + `CountDownLatch` 竞争测试。
```

Add this bullet under `### 2.4 本轮新增修复（2026-06-16）`:

```markdown
- ✅ **消费链真实两线程并发测试**：新增 service 层并发集成测试，使用 `ExecutorService` + `CountDownLatch` 让两个真实线程同时进入 `TransactionMountMsgService.saveTransactionMountMsg`，以不同交易记录竞争同一条开放消费链；通过最终链金额、边金额和尾节点状态断言 `for update` 分配路径不会重复消费同一开放链。
```

If Task 2 changed production SQL, append this sentence to the same bullet:

```markdown
并发红灯后将开放链查询调整为 lock-first CTE，使同一尾节点/币种的开放链先加锁再计算窗口前缀。
```

In `## 7. 下一轮建议优先级`, remove:

```markdown
1. 双挂载 / 并发分配的真实两线程测试。
```

Renumber the remaining structural-refactor priority as item 1.

- [ ] **Step 3: Run focused Failsafe verification**

Run:

```powershell
.\mvnw.cmd -Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest verify
```

Expected: PASS. Record the exact Failsafe count for the new test.

- [ ] **Step 4: Run full unit tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS with zero failures and zero errors. Record the final Surefire test count.

- [ ] **Step 5: Run full Maven verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS if Docker/Testcontainers is available. Record final Surefire and Failsafe counts. If Docker, image registry, or network access fails outside Java code, copy the exact failure text into the audit document and final report.

- [ ] **Step 6: Update verification counts in the audit document**

In `docs/code-quality-audit-status.md`, update:

- the top `验证手段` line,
- `单元测试` count,
- `集成测试` count,
- `## 6. 验证记录与待办`.

Use exact counts from Steps 3-5. The new focused line should look like this with the real count:

```markdown
- ✅ focused failsafe 通过：`.\mvnw.cmd -Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest verify`，1 test passed（Failures 0 / Errors 0 / Skipped 0）。
```

- [ ] **Step 7: Commit audit status update**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "docs: 更新消费链并发测试审计状态"
```

---

## Task 4: Final Review and Completion

**Files:**
- No planned code changes. Fix only issues found by final verification or review.

- [ ] **Step 1: Search for stale audit wording**

Run:

```powershell
rg -n "缺真实两线程并发测试|双挂载 / 并发分配的真实两线程测试|仅靠反射断言注解" docs/code-quality-audit-status.md
```

Expected: no output.

Historical mentions under `docs/superpowers/specs` and `docs/superpowers/plans` are acceptable because they describe the original problem.

- [ ] **Step 2: Verify the new test is routed through Failsafe**

Run:

```powershell
.\mvnw.cmd -Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest verify
```

Expected: PASS with the new integration test executed by Failsafe.

- [ ] **Step 3: Check whitespace, worktree state, and commit authors**

Run:

```powershell
git diff --check
git status --short --branch
git log --format="%h %an <%ae> %s" -8
```

Expected:
- `git diff --check` has no output.
- `git status --short --branch` shows a clean `dev` branch.
- New commits use `GPT5.5XH <gpt5.5xh@example.local>` and Chinese commit messages.

- [ ] **Step 4: Final report**

Report:

```text
已完成：消费链真实两线程并发集成测试，覆盖两个线程同时进入真实事务并竞争同一开放链；如有生产修复，说明锁 SQL 最小改动；审计状态已更新。
验证：列出 focused Failsafe、mvnw test、mvnw verify 的结果和测试数量。
提交：列出本轮新增提交哈希和中文提交信息。
残余：其余结构性重构和 Low/Info 项仍按审计清单后续推进。
```
