# Consume Chain Query Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the six duplicated start/end/node consume-chain repository queries with one unified node-filter query while preserving current service/controller behavior.

**Architecture:** Add a small `ConsumeChainNodeFilter` enum, add one JPQL repository method that takes the enum plus optional `isLoop`, then route all existing `ConsumeChainQueryService` public methods through shared private helpers. Keep mounted-transaction and edge-query paths separate. Use repository-level integration coverage, service mock tests, reflection contract tests, and full Surefire verification.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL/Testcontainers integration tests, JUnit 5, Mockito, Maven Surefire/Failsafe.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-17-consume-chain-query-unification-design.md`.

In scope:

- Add `ConsumeChainNodeFilter` with `START`, `END`, and `NODE`.
- Replace `ConsumeChainRepository` start/end/node query method family with `findByNodeFilter(...)`.
- Keep `findDistinctByRelatedTransactionMount(...)` unchanged.
- Keep every public `ConsumeChainQueryService` method signature unchanged.
- Preserve current Chinese exception messages and exception classes.
- Preserve `Slice`/`Pageable` behavior and batched edge hydration.
- Update audit status after verification.

Out of scope:

- Controller route or request parameter changes.
- `/consume-chains/edges` query refactor.
- Returning-flow-rate query refactor.
- Global exception mapping changes.
- Sort, page, size, or `PageRequestUtil` changes.
- Service write-pipeline template refactor.

## File Structure

- Create `src/main/java/com/cooperativesolutionism/nmsci/enumeration/ConsumeChainNodeFilter.java`: stable selector for start/end/node query roles.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`: add unified node-filter query and remove six duplicated derived/custom node-query methods.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`: delegate existing public methods to shared private id/pubkey/node helpers.
- Create `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java`: real database coverage for unified query semantics.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainQueryOptimizationTest.java`: update mocks/verifications to unified repository method.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`: protect service public signatures and new repository method signature; assert old repository methods are absent.
- Modify `docs/code-quality-audit-status.md`: record the repair and verification results.

---

## Task 1: Add Failing Repository Contract For Unified Node Filter

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java`

- [ ] **Step 1: Write the failing repository integration test**

Create `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java`:

```java
package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
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
        insertEdge(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                edgeOnly, other, edgeChain, record, mount, false, 30L);

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

    private com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg node(UUID id) {
        com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg node =
                new com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg();
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
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest" test
```

Expected: FAIL during test compilation because `ConsumeChainNodeFilter` and `ConsumeChainRepository.findByNodeFilter(...)` do not exist.

- [ ] **Step 3: Commit is not allowed yet**

Do not commit the failing test alone. Continue to Task 2 to make the test compile and pass, then commit test + repository code together.

---

## Task 2: Add Unified Repository Query

**Files:**
- Create: `src/main/java/com/cooperativesolutionism/nmsci/enumeration/ConsumeChainNodeFilter.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java`

- [ ] **Step 1: Add the node-filter enum**

Create `src/main/java/com/cooperativesolutionism/nmsci/enumeration/ConsumeChainNodeFilter.java`:

```java
package com.cooperativesolutionism.nmsci.enumeration;

public enum ConsumeChainNodeFilter {
    START,
    END,
    NODE
}
```

- [ ] **Step 2: Add unified repository method**

In `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`, add the enum import:

```java
import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
```

Replace these six methods:

```java
Slice<ConsumeChain> findByStart(FlowNodeRegisterMsg start, Pageable pageable);

Slice<ConsumeChain> findByStartAndIsLoop(FlowNodeRegisterMsg start, Boolean isLoop, Pageable pageable);

Slice<ConsumeChain> findByEnd(FlowNodeRegisterMsg end, Pageable pageable);

Slice<ConsumeChain> findByEndAndIsLoop(FlowNodeRegisterMsg end, Boolean isLoop, Pageable pageable);

@Query("""
        select c
        from ConsumeChain c
        where c.start = :node
            or c.end = :node
            or exists (
                select 1
                from ConsumeChainEdge e
                where e.chain = c
                    and (e.source = :node or e.target = :node)
            )
        """)
Slice<ConsumeChain> findDistinctByNode(FlowNodeRegisterMsg node, Pageable pageable);

@Query("""
        select c
        from ConsumeChain c
        where c.isLoop = :isLoop
            and (
                c.start = :node
                or c.end = :node
                or exists (
                    select 1
                    from ConsumeChainEdge e
                    where e.chain = c
                        and (e.source = :node or e.target = :node)
                )
            )
        """)
Slice<ConsumeChain> findDistinctByNodeAndIsLoop(FlowNodeRegisterMsg node, Boolean isLoop, Pageable pageable);
```

with:

```java
@Query("""
        select distinct c
        from ConsumeChain c
        where (:isLoop is null or c.isLoop = :isLoop)
            and (
                (:filter = com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter.START
                    and c.start = :node)
                or (:filter = com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter.END
                    and c.end = :node)
                or (:filter = com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter.NODE
                    and (
                        c.start = :node
                        or c.end = :node
                        or exists (
                            select 1
                            from ConsumeChainEdge e
                            where e.chain = c
                                and (e.source = :node or e.target = :node)
                        )
                    ))
            )
        """)
Slice<ConsumeChain> findByNodeFilter(
        @Param("filter") ConsumeChainNodeFilter filter,
        @Param("node") FlowNodeRegisterMsg node,
        @Param("isLoop") Boolean isLoop,
        Pageable pageable
);
```

Keep `findDistinctByRelatedTransactionMount(...)` and `lockOpenChainsForAllocation(...)` unchanged.

- [ ] **Step 3: Run the repository test and verify it passes**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest" test
```

Expected: PASS. The expected test count is 1 test with `Failures: 0, Errors: 0, Skipped: 0`.

If Spring Data rejects enum literals in JPQL, replace the three enum comparisons in the query with parameterized SpEL string comparisons:

```java
(:#{#filter.name()} = 'START' and c.start = :node)
(:#{#filter.name()} = 'END' and c.end = :node)
(:#{#filter.name()} = 'NODE' and (...))
```

Then rerun the same test and require it to pass before continuing.

- [ ] **Step 4: Commit repository unification foundation**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/enumeration/ConsumeChainNodeFilter.java src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 统一消费链节点仓储查询"
```

---

## Task 3: Update Service Delegation And Focused Unit Tests

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainQueryOptimizationTest.java`

- [ ] **Step 1: Update service mock tests to expect the unified repository method**

In `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainQueryOptimizationTest.java`, add:

```java
import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
```

Replace repository stubbing/verifications in existing tests as follows.

In `getConsumeChainByStartLoadsEdgesForAllChainsInOneBatch`, replace:

```java
when(chainRepository.findByStart(start, pageable))
        .thenReturn(new SliceImpl<>(List.of(firstChain, secondChain), pageable, false));
```

with:

```java
when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, start, null, pageable))
        .thenReturn(new SliceImpl<>(List.of(firstChain, secondChain), pageable, false));
```

In `getConsumeChainByNodeLoadsAllContainingChainsAndEdgesInOneBatch`, replace:

```java
when(chainRepository.findDistinctByNode(node, pageable))
        .thenReturn(new SliceImpl<>(List.of(firstChain, secondChain), pageable, false));
```

with:

```java
when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.NODE, node, null, pageable))
        .thenReturn(new SliceImpl<>(List.of(firstChain, secondChain), pageable, false));
```

and replace:

```java
verify(chainRepository).findDistinctByNode(node, pageable);
```

with:

```java
verify(chainRepository).findByNodeFilter(ConsumeChainNodeFilter.NODE, node, null, pageable);
```

In `getConsumeChainByPubkeyUsesExactlyOnePubkeyFilterAndLoadsEdgesInOneBatch`, replace:

```java
when(chainRepository.findByStartAndIsLoop(start, true, pageable))
        .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
```

with:

```java
when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.START, start, true, pageable))
        .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
```

and replace:

```java
verify(chainRepository).findByStartAndIsLoop(start, true, pageable);
```

with:

```java
verify(chainRepository).findByNodeFilter(ConsumeChainNodeFilter.START, start, true, pageable);
```

In `getConsumeChainByRelatedIdUsesExactlyOneIdFilterAndLoadsEdgesInOneBatch`, replace:

```java
when(chainRepository.findDistinctByNodeAndIsLoop(node, true, pageable))
        .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
```

with:

```java
when(chainRepository.findByNodeFilter(ConsumeChainNodeFilter.NODE, node, true, pageable))
        .thenReturn(new SliceImpl<>(List.of(chain), pageable, false));
```

and replace:

```java
verify(chainRepository).findDistinctByNodeAndIsLoop(node, true, pageable);
```

with:

```java
verify(chainRepository).findByNodeFilter(ConsumeChainNodeFilter.NODE, node, true, pageable);
```

Keep the mounted-transaction test stubbing `findDistinctByRelatedTransactionMount(...)`.

- [ ] **Step 2: Add service error-message regression test**

Add this test method to `ConsumeChainQueryOptimizationTest`:

```java
@Test
void nodeIdHelpersPreserveCurrentNullAndNotFoundMessages() {
    ConsumeChainQueryService service = new ConsumeChainQueryService();
    FlowNodeRegisterMsgRepository flowNodeRepository = mock(FlowNodeRegisterMsgRepository.class);
    ConsumeChainRepository chainRepository = mock(ConsumeChainRepository.class);
    ConsumeChainEdgeRepository edgeRepository = mock(ConsumeChainEdgeRepository.class);
    ConsumeChainSupport support = support(flowNodeRepository, edgeRepository);
    injectQuery(service, support, flowNodeRepository, chainRepository, edgeRepository, mock(TransactionMountMsgRepository.class));
    UUID missing = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Pageable pageable = PageRequest.of(0, 50);

    IllegalArgumentException startNull = assertThrows(
            IllegalArgumentException.class,
            () -> service.getConsumeChainByStart(null, pageable)
    );
    assertEquals("起点ID不能为空", startNull.getMessage());

    when(flowNodeRepository.findById(missing)).thenReturn(Optional.empty());
    com.cooperativesolutionism.nmsci.exception.NotFoundException startMissing = assertThrows(
            com.cooperativesolutionism.nmsci.exception.NotFoundException.class,
            () -> service.getConsumeChainByStart(missing, pageable)
    );
    assertEquals("起点ID不存在", startMissing.getMessage());

    IllegalArgumentException endNull = assertThrows(
            IllegalArgumentException.class,
            () -> service.getConsumeChainByEnd(null, pageable)
    );
    assertEquals("终点ID不能为空", endNull.getMessage());

    IllegalArgumentException nodeNull = assertThrows(
            IllegalArgumentException.class,
            () -> service.getConsumeChainByNode(null, pageable)
    );
    assertEquals("节点ID不能为空", nodeNull.getMessage());
}
```

The class already imports `Optional`, `PageRequest`, and assertion methods used here. Add a fully qualified `NotFoundException` in the code above or import it.

- [ ] **Step 3: Run focused service tests and verify expected failures**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainQueryOptimizationTest" test
```

Expected before service implementation: FAIL or compile failure because `ConsumeChainQueryService` still calls the removed repository methods.

- [ ] **Step 4: Refactor `ConsumeChainQueryService` to shared helpers**

In `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`, add:

```java
import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
```

Replace these public methods with delegating implementations:

```java
public Slice<ConsumeChainResponseDTO> getConsumeChainByStart(UUID id, Pageable pageable) {
    return queryByNodeId(ConsumeChainNodeFilter.START, id, null, "起点", pageable);
}

public Slice<ConsumeChainResponseDTO> getConsumeChainByStartAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
    return queryByNodeId(ConsumeChainNodeFilter.START, id, isLoop, "起点", pageable);
}

public Slice<ConsumeChainResponseDTO> getConsumeChainByEnd(UUID id, Pageable pageable) {
    return queryByNodeId(ConsumeChainNodeFilter.END, id, null, "终点", pageable);
}

public Slice<ConsumeChainResponseDTO> getConsumeChainByEndAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
    return queryByNodeId(ConsumeChainNodeFilter.END, id, isLoop, "终点", pageable);
}

public Slice<ConsumeChainResponseDTO> getConsumeChainByNode(UUID id, Pageable pageable) {
    return queryByNodeId(ConsumeChainNodeFilter.NODE, id, null, "节点", pageable);
}

public Slice<ConsumeChainResponseDTO> getConsumeChainByNodeAndIsLoop(UUID id, Boolean isLoop, Pageable pageable) {
    return queryByNodeId(ConsumeChainNodeFilter.NODE, id, isLoop, "节点", pageable);
}
```

Replace the body of `getConsumeChainByRelatedId(...)` with:

```java
if (countProvided(start, end, node) != 1) {
    throw new IllegalArgumentException("必须且只能提供start、end、node中的一个");
}

if (start != null) {
    return queryByNodeId(ConsumeChainNodeFilter.START, start, isLoop, "起点", pageable);
}

if (end != null) {
    return queryByNodeId(ConsumeChainNodeFilter.END, end, isLoop, "终点", pageable);
}

return queryByNodeId(ConsumeChainNodeFilter.NODE, node, isLoop, "节点", pageable);
```

Replace the body of `getConsumeChainByPubkey(...)` with:

```java
if (countProvided(startPubkey, endPubkey, nodePubkey) != 1) {
    throw new IllegalArgumentException("必须且只能提供startPubkey、endPubkey、nodePubkey中的一个");
}

if (startPubkey != null) {
    return queryByNodePubkey(ConsumeChainNodeFilter.START, startPubkey, isLoop, "起点", pageable);
}

if (endPubkey != null) {
    return queryByNodePubkey(ConsumeChainNodeFilter.END, endPubkey, isLoop, "终点", pageable);
}

return queryByNodePubkey(ConsumeChainNodeFilter.NODE, nodePubkey, isLoop, "节点", pageable);
```

Add these private helpers above `getConsumeChainById(...)`:

```java
private Slice<ConsumeChainResponseDTO> queryByNodeId(
        ConsumeChainNodeFilter filter,
        UUID id,
        Boolean isLoop,
        String label,
        Pageable pageable
) {
    if (id == null) {
        throw new IllegalArgumentException(label + "ID不能为空");
    }

    FlowNodeRegisterMsg node = flowNodeRegisterMsgRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(label + "ID不存在"));

    return queryByNode(filter, node, isLoop, pageable);
}

private Slice<ConsumeChainResponseDTO> queryByNodePubkey(
        ConsumeChainNodeFilter filter,
        byte[] pubkey,
        Boolean isLoop,
        String label,
        Pageable pageable
) {
    FlowNodeRegisterMsg node = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(pubkey, label);

    return queryByNode(filter, node, isLoop, pageable);
}

private Slice<ConsumeChainResponseDTO> queryByNode(
        ConsumeChainNodeFilter filter,
        FlowNodeRegisterMsg node,
        Boolean isLoop,
        Pageable pageable
) {
    Slice<ConsumeChain> consumeChains = consumeChainRepository.findByNodeFilter(filter, node, isLoop, pageable);

    return getConsumeChainResponseDTOSlice(consumeChains);
}
```

- [ ] **Step 5: Run focused service tests and verify they pass**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainQueryOptimizationTest" test
```

Expected: PASS. Current expected count after adding one test is 7 tests in this class.

- [ ] **Step 6: Commit service delegation**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainQueryOptimizationTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 复用消费链节点查询分派"
```

---

## Task 4: Update Reflection Contract And Run Targeted Verification

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`
- Test: repository, service, and pagination focused tests.

- [ ] **Step 1: Update pagination reflection test imports**

In `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`, add:

```java
import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
```

- [ ] **Step 2: Update repository method signature assertions**

In `serviceAndRepositoryQueriesUseSliceAndPageable()`, keep all service method assertions unchanged:

```java
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByMountedTransaction", UUID.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByStart", UUID.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByStartAndIsLoop", UUID.class, Boolean.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByEnd", UUID.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByEndAndIsLoop", UUID.class, Boolean.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByNode", UUID.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByNodeAndIsLoop", UUID.class, Boolean.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByRelatedId", UUID.class, UUID.class, UUID.class, Boolean.class, Pageable.class);
assertSliceMethod(ConsumeChainQueryService.class, "getConsumeChainByPubkey", byte[].class, byte[].class, byte[].class, Boolean.class, Pageable.class);
assertSliceMethod(
        ConsumeChainQueryService.class,
        "getConsumeChainEdgesById",
        UUID.class, UUID.class, short.class, long.class, long.class, Pageable.class
);
assertSliceMethod(
        ConsumeChainQueryService.class,
        "getConsumeChainEdgesByPubkey",
        byte[].class, byte[].class, short.class, long.class, long.class, Pageable.class
);
```

Replace the old repository method assertions:

```java
assertSliceMethod(ConsumeChainRepository.class, "findByStart", FlowNodeRegisterMsg.class, Pageable.class);
assertSliceMethod(ConsumeChainRepository.class, "findByStartAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
assertSliceMethod(ConsumeChainRepository.class, "findByEnd", FlowNodeRegisterMsg.class, Pageable.class);
assertSliceMethod(ConsumeChainRepository.class, "findByEndAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
assertSliceMethod(ConsumeChainRepository.class, "findDistinctByNode", FlowNodeRegisterMsg.class, Pageable.class);
assertSliceMethod(ConsumeChainRepository.class, "findDistinctByNodeAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
```

with:

```java
assertSliceMethod(
        ConsumeChainRepository.class,
        "findByNodeFilter",
        ConsumeChainNodeFilter.class, FlowNodeRegisterMsg.class, Boolean.class, Pageable.class
);
assertRepositoryMethodAbsent("findByStart", FlowNodeRegisterMsg.class, Pageable.class);
assertRepositoryMethodAbsent("findByStartAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
assertRepositoryMethodAbsent("findByEnd", FlowNodeRegisterMsg.class, Pageable.class);
assertRepositoryMethodAbsent("findByEndAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
assertRepositoryMethodAbsent("findDistinctByNode", FlowNodeRegisterMsg.class, Pageable.class);
assertRepositoryMethodAbsent("findDistinctByNodeAndIsLoop", FlowNodeRegisterMsg.class, Boolean.class, Pageable.class);
```

Keep:

```java
assertSliceMethod(ConsumeChainRepository.class, "findDistinctByRelatedTransactionMount", TransactionMountMsg.class, Pageable.class);
```

Add this helper near `assertControllerMethodAbsent(...)`:

```java
private void assertRepositoryMethodAbsent(String name, Class<?>... parameterTypes) {
    assertThrows(NoSuchMethodException.class, () -> ConsumeChainRepository.class.getMethod(name, parameterTypes));
}
```

- [ ] **Step 3: Run targeted tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest,ConsumeChainQueryOptimizationTest,ConsumeChainPaginationTest" test
```

Expected: PASS. Expected count is 1 repository test + 7 query optimization tests + 3 pagination tests = 11 tests.

- [ ] **Step 4: Run stale method scans**

Run:

```powershell
rg -n "findByStart|findByStartAndIsLoop|findByEnd|findByEndAndIsLoop|findDistinctByNode|findDistinctByNodeAndIsLoop" src\main\java src\test\java
```

Expected: output may appear only in `ConsumeChainPaginationTest` absent-method assertions and in audit/docs if docs are included by mistake. There must be no usage in `src/main/java` and no mock stubbing/verifying old repository methods in tests.

Run:

```powershell
rg -n "findByNodeFilter|ConsumeChainNodeFilter" src\main\java src\test\java
```

Expected: matches in the new enum, repository, service, and focused tests.

- [ ] **Step 5: Commit reflection contract update**

Run:

```powershell
git add src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "test: 更新消费链查询契约"
```

---

## Task 5: Update Audit Status And Run Full Verification

**Files:**
- Modify: `docs/code-quality-audit-status.md`

- [ ] **Step 1: Run full unit verification**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS. The previous full Surefire count was 168; after adding `ConsumeChainRepositoryNodeFilterTest` and one new service test, expected total is 170 tests if no existing test count changes. Use the actual Maven result in the audit document.

- [ ] **Step 2: Optionally run full verify if Docker is available**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS when Docker/Testcontainers is available. The previous Failsafe count was 35. Record the actual Surefire and Failsafe counts. If Docker/Testcontainers is unavailable, record the exact failure reason and keep the Maven Surefire result separate.

- [ ] **Step 3: Update audit status**

In `docs/code-quality-audit-status.md`, update the top `codex 修复范围` line to include:

```markdown
`docs/superpowers/specs/2026-06-17-consume-chain-query-unification-design.md` / `docs/superpowers/plans/2026-06-17-consume-chain-query-unification.md`（消费链查询统一化）
```

Update the top `验证手段` line to include the focused targeted command and actual count:

```markdown
targeted surefire `ConsumeChainRepositoryNodeFilterTest,ConsumeChainQueryOptimizationTest,ConsumeChainPaginationTest`（11 tests）
```

If full `mvnw test` count changed to 170, update full test count from 168 to 170. If `mvnw verify` ran, update Surefire/Failsafe counts to actual values.

Under `### 2.4 本轮新增修复（2026-06-16）`, add a new 2026-06-17 bullet or update the heading to include 2026-06-17:

```markdown
- ✅ **消费链查询统一化**：新增 `ConsumeChainNodeFilter` 和统一 `ConsumeChainRepository.findByNodeFilter(...)`，收敛 start/end/node 与可选 `isLoop` 的 6 个重复查询方法；`ConsumeChainQueryService` 保留现有 public 方法和错误文案，通过私有 id/pubkey/node helper 复用分派逻辑，挂载交易查询与 `/consume-chains/edges` 路径保持独立。
```

In `## 3. 有意延后`, remove or narrow this delayed bullet:

```markdown
- `ConsumeChainQueryService` ~8–10 个近重复 `getConsumeChainBy*` 方法（Low）。
```

Do not remove unrelated delayed items such as write-service template refactor, `BlockAssembler.findMessages`, repository reverse dependency, `WebMvcConfig`, or error-contract/API polish.

In `## 6. 验证记录与待办`, add:

```markdown
- ✅ targeted surefire 通过：`.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest,ConsumeChainQueryOptimizationTest,ConsumeChainPaginationTest" test`，11 tests passed（Failures 0 / Errors 0 / Skipped 0）。
```

Update the full `mvnw test` and `mvnw verify` bullets with actual counts from Step 1 and Step 2.

- [ ] **Step 4: Run audit stale scan**

Run:

```powershell
rg -n "ConsumeChainQueryService.*getConsumeChainBy|消费链查询统一化|findByNodeFilter|ConsumeChainNodeFilter" docs\code-quality-audit-status.md
```

Expected: matches should include the new completed repair and may include historical context only if clearly not a delayed unresolved item. The old delayed bullet about repeated `getConsumeChainBy*` methods must not remain under `## 3`.

- [ ] **Step 5: Commit audit update**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "docs: 更新消费链查询统一化审计状态"
```

---

## Task 6: Final Review And Completion

**Files:**
- No planned file edits. Fix only issues found by verification or review.

- [ ] **Step 1: Run final stale code scans**

Run:

```powershell
rg -n "findByStart|findByStartAndIsLoop|findByEnd|findByEndAndIsLoop|findDistinctByNode|findDistinctByNodeAndIsLoop" src\main\java
```

Expected: no output.

Run:

```powershell
rg -n "findByNodeFilter|ConsumeChainNodeFilter" src\main\java src\test\java
```

Expected: matches in the enum, repository, service, and tests.

- [ ] **Step 2: Run final focused verification**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest,ConsumeChainQueryOptimizationTest,ConsumeChainPaginationTest" test
```

Expected: PASS with the actual targeted test count recorded in Task 5.

- [ ] **Step 3: Run workspace and author checks**

Run:

```powershell
git diff --check
git status --short --branch
git log --format="%h %an <%ae> | %cn <%ce> | %s" -10
```

Expected:

- `git diff --check` has no output.
- `git status --short --branch` shows a clean branch.
- New commits use `GPT5.5XH <gpt5.5xh@example.local>` for both author and committer.
- Commit messages are Chinese.

- [ ] **Step 4: Final report**

Report:

```text
已完成：消费链查询统一化，新增 ConsumeChainNodeFilter 和 ConsumeChainRepository.findByNodeFilter(...)，删除 start/end/node × isLoop 的重复仓储方法；ConsumeChainQueryService public 方法、controller API、错误文案和分页行为保持不变。
验证：列出 targeted surefire、mvnw test、mvnw verify 的实际结果和测试数量。
提交：列出本轮新增提交哈希和中文提交信息。
残余：写 Service 模板化、错误契约/API 打磨、BlockAssembler 类型派发等仍按审计清单留待后续。
```
