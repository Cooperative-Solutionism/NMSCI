# Consume Chain API Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded pagination to `/consume-chains/edges` and make query-time not-found semantics consistently return 404 without changing bad-request semantics.

**Architecture:** Keep the existing Spring MVC controller/service/repository layering. Use `SliceResponseDTO` and `PageRequestUtil` for the edge endpoint, keep native SQL count-free with explicit `limit`/`offset`, and use `NotFoundException` only for resource/state misses. API and audit docs are updated after behavior is covered by tests.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Data JPA, PostgreSQL native queries, JUnit 5, Mockito, MockMvc, Maven.

---

## Scope Check

This plan implements the approved scope from `docs/superpowers/specs/2026-06-16-consume-chain-api-contract-design.md`:

- `/consume-chains/edges` pagination and hard limit.
- Query-time 404 semantics for missing frozen/authorized/pubkey resources.
- Documentation and verification updates.

It does not implement source-code hash fixes, global error envelope redesign, POST status code changes, service template refactors, or protocol constant extraction.

## File Structure

- Modify `src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java`: add `page`/`size`, return `SliceResponseDTO<ConsumeChainEdge>`, and construct edge query `Pageable`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`: change edge query methods to return `Slice<ConsumeChainEdge>`, request `pageSize + 1`, and build `SliceImpl`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainEdgeRepository.java`: add native-query `limit`/`offset` parameters for edge queries.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`: missing lock by central pubkey throws `NotFoundException`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeLockedMsgService.java`: missing lock by flow-node pubkey throws `NotFoundException`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyEmpowerMsgService.java`: missing empowerment by flow-node pubkey throws `NotFoundException` and avoids duplicate existence lookup.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`: reflection contract for edge endpoint and service methods.
- Create `src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java`: unit coverage for `size + 1`, `hasNext`, and offset behavior.
- Create `src/test/java/com/cooperativesolutionism/nmsci/service/ProtocolNotFoundSemanticsTest.java`: service-level 404/400 boundary for pubkey query methods.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolLifecycleIntegrationTest.java`: integration coverage that `/consume-chains/edges` returns slice metadata.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java`: API error contract for page size, missing target, mixed id/pubkey, and missing target pubkey.
- Modify `docs/API.md`: document paginated edge response and 404 semantics.
- Modify `docs/code-quality-audit-status.md`: mark the two approved next-round items as fixed and record verification status. This file is currently untracked; when implementation updates it, stage it deliberately.

---

## Task 1: Add Failing Pagination Contract Tests

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolLifecycleIntegrationTest.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java`

- [ ] **Step 1: Add reflection checks for the edge endpoint and service signatures**

In `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`, append these assertions inside `serviceAndRepositoryQueriesUseSliceAndPageable()` after the existing consume-chain service assertions:

```java
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

Append this assertion inside `controllerBatchQueriesReturnSliceResponse()` after the existing `queryConsumeChains` assertion:

```java
assertSliceResponseControllerMethod(
        "getConsumeChainEdges",
        String.class, String.class, String.class, String.class,
        short.class, long.class, long.class, int.class, int.class
);
```

- [ ] **Step 2: Add unit tests for `pageSize + 1`, `hasNext`, and offset**

Create `src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java`:

```java
package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumeChainQueryServiceTest {

    private static final Sort EDGE_SORT = Sort.by(
            Sort.Order.desc("relatedTransactionMountTimestamp"),
            Sort.Order.desc("id")
    );

    @Test
    void edgeTargetQueryRequestsOneExtraRowAndReportsHasNext() {
        UUID targetId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ConsumeChainEdgeRepository repository = mock(ConsumeChainEdgeRepository.class);
        when(repository.findConsumeChainEdgesByTarget(targetId, (short) 1, 0L, Long.MAX_VALUE, 3, 0L))
                .thenReturn(List.of(
                        edge("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        edge("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                        edge("cccccccc-cccc-cccc-cccc-cccccccccccc")
                ));

        ConsumeChainQueryService service = new ConsumeChainQueryService();
        ReflectionTestUtils.setField(service, "consumeChainEdgeRepository", repository);
        Pageable pageable = PageRequest.of(0, 2, EDGE_SORT);

        Slice<ConsumeChainEdge> result = service.getConsumeChainEdgesById(null, targetId, (short) 1, 0L, Long.MAX_VALUE, pageable);

        assertEquals(2, result.getNumberOfElements());
        assertTrue(result.hasNext());
        assertEquals(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), result.getContent().get(0).getId());
        assertEquals(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), result.getContent().get(1).getId());
        verify(repository).findConsumeChainEdgesByTarget(targetId, (short) 1, 0L, Long.MAX_VALUE, 3, 0L);
    }

    @Test
    void edgeSourceTargetQueryPassesPageOffsetToRepository() {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID targetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        ConsumeChainEdgeRepository repository = mock(ConsumeChainEdgeRepository.class);
        when(repository.findConsumeChainEdges(sourceId, targetId, (short) 1, 100L, 200L, 3, 2L))
                .thenReturn(List.of(edge("dddddddd-dddd-dddd-dddd-dddddddddddd")));

        ConsumeChainQueryService service = new ConsumeChainQueryService();
        ReflectionTestUtils.setField(service, "consumeChainEdgeRepository", repository);
        Pageable pageable = PageRequest.of(1, 2, EDGE_SORT);

        Slice<ConsumeChainEdge> result = service.getConsumeChainEdgesById(sourceId, targetId, (short) 1, 100L, 200L, pageable);

        assertEquals(1, result.getNumberOfElements());
        verify(repository).findConsumeChainEdges(sourceId, targetId, (short) 1, 100L, 200L, 3, 2L);
    }

    private ConsumeChainEdge edge(String id) {
        ConsumeChainEdge edge = new ConsumeChainEdge();
        edge.setId(UUID.fromString(id));
        return edge;
    }
}
```

- [ ] **Step 3: Add integration coverage for the paginated edge response shape**

In `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolLifecycleIntegrationTest.java`, add this test after `queryConsumeChainByIdUsesExistingSliceResponseFormat()`:

```java
@Test
void queryConsumeChainEdgesUsesSliceResponseFormat() throws Exception {
    UUID flowNodeId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    UUID empowerId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    UUID recordId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    UUID mountId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    sendFlowNodeRegister(flowNodeId, TestKeyPairs.FLOW_NODE_A);
    sendCentralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A);
    sendTransactionRecord(recordId, 1200L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);
    sendTransactionMount(mountId, recordId, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A);

    mockMvc.perform(get("/consume-chains/edges")
                    .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()))
                    .param("page", "0")
                    .param("size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.content[0].id").exists())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(50))
            .andExpect(jsonPath("$.data.numberOfElements").value(1))
            .andExpect(jsonPath("$.data.hasNext").value(false))
            .andExpect(jsonPath("$.data.hasPrevious").value(false));
}
```

- [ ] **Step 4: Add edge endpoint error contract tests**

In `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java`, add these tests near the existing consume-chain and returning-flow-rate error tests:

```java
@Test
void rejectsConsumeChainEdgesWhenPageSizeExceedsLimit() throws Exception {
    mockMvc.perform(get("/consume-chains/edges")
                    .param("targetId", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa").toString())
                    .param("size", "201"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("分页大小不能超过200")));
}

@Test
void rejectsConsumeChainEdgesWhenTargetIsMissing() throws Exception {
    mockMvc.perform(get("/consume-chains/edges"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("targetId 不能为空")));
}

@Test
void rejectsConsumeChainEdgesWhenIdAndPubkeyAreMixed() throws Exception {
    mockMvc.perform(get("/consume-chains/edges")
                    .param("targetId", UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb").toString())
                    .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("id 与 pubkey 查询参数不能混用")));
}

@Test
void rejectsConsumeChainEdgesWhenTargetPubkeyIsNotRegistered() throws Exception {
    mockMvc.perform(get("/consume-chains/edges")
                    .param("targetPubkey", ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_B.pubkey())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("目标流转节点公钥")))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("不存在")));
}

@Test
void rejectsConsumeChainEdgesWhenTargetPubkeyHasWrongLength() throws Exception {
    mockMvc.perform(get("/consume-chains/edges")
                    .param("targetPubkey", "00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("目标流转节点公钥不能为空或长度不为33字节")));
}
```

- [ ] **Step 5: Run targeted tests and verify the new contract is failing**

Run:

```powershell
.\mvnw.cmd -Dtest=ConsumeChainPaginationTest,ConsumeChainQueryServiceTest,ProtocolLifecycleIntegrationTest,ProtocolErrorIntegrationTest test
```

Expected: FAIL. The reflection tests should fail because `getConsumeChainEdges` and edge service methods do not yet accept `Pageable`; the integration response-shape test should fail because the endpoint still returns a bare list.

- [ ] **Step 6: Keep red test changes uncommitted until Task 2**

Do not commit after the red test run. Leave these test changes in the worktree so Task 2 can make them pass and commit tests plus implementation together.

Expected: `git status --short` shows the four changed test files and no production code changes.

---

## Task 2: Implement Paginated `/consume-chains/edges`

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainEdgeRepository.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolLifecycleIntegrationTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java`

- [ ] **Step 1: Update repository native queries to accept limit and offset**

In `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainEdgeRepository.java`, replace the two edge query methods with:

```java
@Query(nativeQuery = true, value = """
        SELECT d.*
        FROM (
            SELECT DISTINCT ON (c.chain) c.*
            FROM consume_chain_edges c
            WHERE c.source = :source
                AND c.target = :target
                AND c.currency_type = :currencyType
                AND c.related_transaction_mount_timestamp BETWEEN :startTime AND :endTime
            ORDER BY c.chain, c.related_transaction_mount_timestamp
        ) d
        ORDER BY d.related_transaction_mount_timestamp DESC, d.id DESC
        LIMIT :limit OFFSET :offset
        """)
List<ConsumeChainEdge> findConsumeChainEdges(
        UUID source,
        UUID target,
        short currencyType,
        Long startTime,
        Long endTime,
        int limit,
        long offset
);

@Query(nativeQuery = true, value = """
        SELECT d.*
        FROM (
            SELECT DISTINCT ON (c.chain) c.*
            FROM consume_chain_edges c
            WHERE c.target = :target
                AND c.currency_type = :currencyType
                AND c.related_transaction_mount_timestamp BETWEEN :startTime AND :endTime
            ORDER BY c.chain, c.related_transaction_mount_timestamp
        ) d
        ORDER BY d.related_transaction_mount_timestamp DESC, d.id DESC
        LIMIT :limit OFFSET :offset
        """)
List<ConsumeChainEdge> findConsumeChainEdgesByTarget(
        UUID target,
        short currencyType,
        Long startTime,
        Long endTime,
        int limit,
        long offset
);
```

Leave the aggregate returning-flow-rate queries unchanged; they intentionally compute over the full filtered set.

- [ ] **Step 2: Change edge query service methods to return `Slice`**

In `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`, replace the two edge query methods with:

```java
/**
 * 按 id 查询消费链边：source 缺省时返回流入 target 的全部边（按 chain 去重）；
 * source、target 均提供时返回 source→target 之间的边。
 */
public Slice<ConsumeChainEdge> getConsumeChainEdgesById(
        UUID sourceId,
        UUID targetId,
        short currencyType,
        long startTime,
        long endTime,
        Pageable pageable
) {
    if (!CurrencyTypeEnum.containsValue(currencyType)) {
        throw new IllegalArgumentException("货币类型错误，必须为以下数值：\n" + CurrencyTypeEnum.getAllEnumDescriptions());
    }
    if (targetId == null) {
        throw new IllegalArgumentException("targetId 不能为空");
    }

    int limit = edgeQueryLimit(pageable);
    long offset = pageable.getOffset();
    List<ConsumeChainEdge> edges = sourceId == null
            ? consumeChainEdgeRepository.findConsumeChainEdgesByTarget(targetId, currencyType, startTime, endTime, limit, offset)
            : consumeChainEdgeRepository.findConsumeChainEdges(sourceId, targetId, currencyType, startTime, endTime, limit, offset);
    return toEdgeSlice(edges, pageable);
}

/**
 * 按 pubkey 查询消费链边：先把 pubkey 解析为流转节点 id，再委托 {@link #getConsumeChainEdgesById}。
 */
public Slice<ConsumeChainEdge> getConsumeChainEdgesByPubkey(
        byte[] sourcePubkey,
        byte[] targetPubkey,
        short currencyType,
        long startTime,
        long endTime,
        Pageable pageable
) {
    FlowNodeRegisterMsg target = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(targetPubkey, "目标");

    if (sourcePubkey == null) {
        return getConsumeChainEdgesById(null, target.getId(), currencyType, startTime, endTime, pageable);
    }

    FlowNodeRegisterMsg source = consumeChainSupport.getFlowNodeRegisterMsgByPubkey(sourcePubkey, "源");
    return getConsumeChainEdgesById(source.getId(), target.getId(), currencyType, startTime, endTime, pageable);
}
```

Add these private helpers before `countProvided`:

```java
private int edgeQueryLimit(Pageable pageable) {
    return Math.addExact(pageable.getPageSize(), 1);
}

private Slice<ConsumeChainEdge> toEdgeSlice(List<ConsumeChainEdge> edges, Pageable pageable) {
    boolean hasNext = edges.size() > pageable.getPageSize();
    List<ConsumeChainEdge> content = hasNext
            ? edges.subList(0, pageable.getPageSize())
            : edges;
    return new SliceImpl<>(List.copyOf(content), pageable, hasNext);
}
```

- [ ] **Step 3: Change controller response to `SliceResponseDTO`**

In `src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java`, add this constant near `CONSUME_CHAIN_QUERY_SORT`:

```java
private static final Sort CONSUME_CHAIN_EDGE_QUERY_SORT = Sort.by(
        Sort.Order.desc("relatedTransactionMountTimestamp"),
        Sort.Order.desc("id")
);
```

Replace `getConsumeChainEdges` with:

```java
@GetMapping("/edges")
public ResponseResult<SliceResponseDTO<ConsumeChainEdge>> getConsumeChainEdges(
        @RequestParam(required = false) String sourceId,
        @RequestParam(required = false) String targetId,
        @RequestParam(required = false) String sourcePubkey,
        @RequestParam(required = false) String targetPubkey,
        @RequestParam(required = false, defaultValue = "1") short currencyType,
        @RequestParam(required = false, defaultValue = "0") long startTime,
        @RequestParam(required = false, defaultValue = "9223372036854775807") long endTime,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
) {
    boolean hasId = notBlank(sourceId) || notBlank(targetId);
    boolean hasPubkey = notBlank(sourcePubkey) || notBlank(targetPubkey);
    if (hasId && hasPubkey) {
        throw new BadRequestException("id 与 pubkey 查询参数不能混用");
    }

    Pageable pageable = PageRequestUtil.of(page, size, CONSUME_CHAIN_EDGE_QUERY_SORT);

    Slice<ConsumeChainEdge> edges;
    if (hasPubkey) {
        if (!notBlank(targetPubkey)) {
            throw new BadRequestException("targetPubkey 不能为空");
        }
        edges = consumeChainQueryService.getConsumeChainEdgesByPubkey(
                pubkey(sourcePubkey), pubkey(targetPubkey), currencyType, startTime, endTime, pageable);
    } else {
        if (!notBlank(targetId)) {
            throw new BadRequestException("targetId 不能为空");
        }
        edges = consumeChainQueryService.getConsumeChainEdgesById(
                uuid(sourceId), uuid(targetId), currencyType, startTime, endTime, pageable);
    }
    return ResponseResult.success(SliceResponseDTO.from(edges));
}
```

Keep the existing `notBlank`, `uuid`, and `pubkey` helpers unchanged.

- [ ] **Step 4: Run targeted pagination tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ConsumeChainPaginationTest,ConsumeChainQueryServiceTest,ProtocolLifecycleIntegrationTest,ProtocolErrorIntegrationTest test
```

Expected: PASS for pagination-related tests. Existing `ProtocolErrorIntegrationTest.rejectsTransactionRecordFromUnempoweredFlowNode` should still return 400; this plan does not change write-path authorization errors.

- [ ] **Step 5: Commit pagination implementation**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainEdgeRepository.java src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolLifecycleIntegrationTest.java src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 分页消费链边查询接口'
```

---

## Task 3: Make Query-Time Not Found Return 404

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/service/ProtocolNotFoundSemanticsTest.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeLockedMsgService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyEmpowerMsgService.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java`

- [ ] **Step 1: Add service tests for 404/400 boundary**

Create `src/test/java/com/cooperativesolutionism/nmsci/service/ProtocolNotFoundSemanticsTest.java`:

```java
package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProtocolNotFoundSemanticsTest {

    @Test
    void centralPubkeyLockLookupByPubkeyReturnsNotFoundWhenMissing() {
        byte[] pubkey = TestKeyPairs.CENTRAL.pubkey();
        CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
        when(repository.findByCentralPubkey(pubkey)).thenReturn(null);
        CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService();
        ReflectionTestUtils.setField(service, "centralPubkeyLockedMsgRepository", repository);

        assertThrows(NotFoundException.class, () -> service.getCentralPubkeyLockedMsgByCentralPubkey(pubkey));
    }

    @Test
    void flowNodeLockLookupByPubkeyReturnsNotFoundWhenMissing() {
        byte[] pubkey = TestKeyPairs.FLOW_NODE_A.pubkey();
        FlowNodeLockedMsgRepository repository = mock(FlowNodeLockedMsgRepository.class);
        when(repository.findByFlowNodePubkey(pubkey)).thenReturn(null);
        FlowNodeLockedMsgService service = new FlowNodeLockedMsgService();
        ReflectionTestUtils.setField(service, "flowNodeLockedMsgRepository", repository);

        assertThrows(NotFoundException.class, () -> service.getFlowNodeLockedMsgByFlowNodePubkey(pubkey));
    }

    @Test
    void centralPubkeyEmpowerLookupByPubkeyReturnsNotFoundWhenMissing() {
        byte[] pubkey = TestKeyPairs.FLOW_NODE_A.pubkey();
        CentralPubkeyEmpowerMsgRepository repository = mock(CentralPubkeyEmpowerMsgRepository.class);
        when(repository.findByFlowNodePubkey(pubkey)).thenReturn(null);
        CentralPubkeyEmpowerMsgService service = new CentralPubkeyEmpowerMsgService();
        ReflectionTestUtils.setField(service, "centralPubkeyEmpowerMsgRepository", repository);

        assertThrows(NotFoundException.class, () -> service.getCentralPubkeyEmpowerMsgByFlowNodePubkey(pubkey));
    }

    @Test
    void malformedPubkeyStillReturnsBadRequestType() {
        CentralPubkeyLockedMsgService centralPubkeyLockedMsgService = new CentralPubkeyLockedMsgService();
        FlowNodeLockedMsgService flowNodeLockedMsgService = new FlowNodeLockedMsgService();
        CentralPubkeyEmpowerMsgService centralPubkeyEmpowerMsgService = new CentralPubkeyEmpowerMsgService();

        assertThrows(IllegalArgumentException.class, () -> centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgByCentralPubkey(new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> flowNodeLockedMsgService.getFlowNodeLockedMsgByFlowNodePubkey(new byte[32]));
        assertThrows(IllegalArgumentException.class, () -> centralPubkeyEmpowerMsgService.getCentralPubkeyEmpowerMsgByFlowNodePubkey(new byte[32]));
    }
}
```

- [ ] **Step 2: Run the not-found tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolNotFoundSemanticsTest test
```

Expected: FAIL because the three missing-resource query methods still throw `IllegalArgumentException`.

- [ ] **Step 3: Update `CentralPubkeyLockedMsgService`**

In `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`, add this import:

```java
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
```

Replace `getCentralPubkeyLockedMsgByCentralPubkey` with:

```java
public CentralPubkeyLockedMsg getCentralPubkeyLockedMsgByCentralPubkey(byte[] centralPubkey) {
    return findCentralPubkeyLockedMsgByCentralPubkey(centralPubkey)
            .orElseThrow(() -> new NotFoundException("中心公钥(" + ByteArrayUtil.bytesToHex(centralPubkey) + ")未冻结"));
}
```

- [ ] **Step 4: Update `FlowNodeLockedMsgService`**

In `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeLockedMsgService.java`, add this import:

```java
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
```

Replace `getFlowNodeLockedMsgByFlowNodePubkey` with:

```java
public FlowNodeLockedMsg getFlowNodeLockedMsgByFlowNodePubkey(byte[] flowNodePubkey) {
    return findFlowNodeLockedMsgByFlowNodePubkey(flowNodePubkey)
            .orElseThrow(() -> new NotFoundException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")未冻结"));
}
```

- [ ] **Step 5: Update `CentralPubkeyEmpowerMsgService`**

In `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyEmpowerMsgService.java`, add this import:

```java
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
```

Replace `getCentralPubkeyEmpowerMsgByFlowNodePubkey` with:

```java
public CentralPubkeyEmpowerMsg getCentralPubkeyEmpowerMsgByFlowNodePubkey(byte[] flowNodePubkey) {
    if (flowNodePubkey == null || flowNodePubkey.length != 33) {
        throw new IllegalArgumentException("流转节点公钥不能为空或长度不正确");
    }

    CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgRepository.findByFlowNodePubkey(flowNodePubkey);
    if (centralPubkeyEmpowerMsg == null) {
        throw new NotFoundException("流转节点公钥(" + ByteArrayUtil.bytesToHex(flowNodePubkey) + ")未授权");
    }

    return centralPubkeyEmpowerMsg;
}
```

- [ ] **Step 6: Run targeted not-found and error tests**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolNotFoundSemanticsTest,ProtocolErrorIntegrationTest test
```

Expected: PASS. `rejectsTransactionRecordFromUnempoweredFlowNode` must remain 400 because it is a write-path protocol validation error, not a query-time missing-resource response.

- [ ] **Step 7: Commit not-found semantic changes**

Run:

```powershell
git add src/test/java/com/cooperativesolutionism/nmsci/service/ProtocolNotFoundSemanticsTest.java src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeLockedMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyEmpowerMsgService.java src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 统一查询缺失资源返回404'
```

---

## Task 4: Update API Docs, Audit Status, and Verify

**Files:**
- Modify: `docs/API.md`
- Modify: `docs/code-quality-audit-status.md`
- Test: all changed tests

- [ ] **Step 1: Update `/consume-chains/edges` API documentation**

In `docs/API.md`, replace the `/consume-chains/edges` paragraph and table with:

```markdown
### GET `/consume-chains/edges`
查询「流入某目标节点」的消费链边集合（用于回流分析）。`target` 必填，`source` 可选（缺省=所有流入 target 的边）。响应 `SliceResponseDTO<ConsumeChainEdge>`，不返回总条数。

| 查询参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `targetId` 或 `targetPubkey` | String | — | **必填**（按所选模式） |
| `sourceId` 或 `sourcePubkey` | String | — | 可选，指定来源节点 |
| `currencyType` | short | `1` | 货币类型 |
| `startTime` / `endTime` | long | `0` / `Long.MAX` | 微秒时间区间（过滤挂载时间） |
| `page` / `size` | int | `0` / `50` | 见 [§1.3](#13-分页)，`size` 最大 200 |

**400**：id 与 pubkey 混用；目标参数（`targetId`/`targetPubkey`）为空；分页参数非法；pubkey 格式或长度非法。
**404**：按 pubkey 查询时目标或来源流转节点不存在。

> 路由说明：字面量 `/consume-chains/edges` 优先于 `/{id}`（UUID），不会被吞。
```

In status code section `1.2`, replace the 404 row with:

```markdown
| 404 | Not Found | 按 `{id}`/高度/哈希/pubkey 查询的资源或状态不存在 |
```

- [ ] **Step 2: Update audit status document**

In `docs/code-quality-audit-status.md`, make these edits:

1. In the “较高价值（建议下一轮优先）” list, change the `/consume-chains/edges` item to:

```markdown
- ✅ **`/consume-chains/edges` 未分页**（Medium，Performance/API）：已改为 `SliceResponseDTO<ConsumeChainEdge>`，新增 `page`/`size`，复用 `PageRequestUtil` 的 200 上限；native query 使用 `limit = size + 1` 与 `offset`，避免 count 查询。
```

2. Change the not-found item to:

```markdown
- ✅ **not-found 语义 400 vs 404 不一致**（Medium，Error/Architecture）：查询期“未冻结/未授权/不存在”改抛 `NotFoundException`→404；格式错误、缺参、非法 pubkey 长度仍保持 400。
```

3. In “下一轮建议优先级”, remove the completed `/consume-chains/edges` and not-found entries, then renumber the remaining items:

```markdown
1. 源码哈希失败兜底 + 排除集对齐（链上完整性）。
2. 双挂载 / 并发分配的真实两线程测试;Secp256k1 原语负路径测试。
3. 其余结构性重构(写 Service 模板化、常量化、去重)按团队节奏推进。
```

4. If `.\mvnw.cmd verify` passes in Step 5, replace “集成测试 | ⬜ 未运行” with:

```markdown
| 集成测试 | ✅ 通过（failsafe / `mvnw verify`） |
```

and replace the §6 verify bullet with:

```markdown
- ✅ `mvnw verify`（failsafe 集成测试，需 Docker）通过；覆盖 JPA/Flyway 启动期 schema validate 与协议生命周期端到端行为。
```

- [ ] **Step 3: Run targeted test suite**

Run:

```powershell
.\mvnw.cmd -Dtest=ConsumeChainPaginationTest,ConsumeChainQueryServiceTest,ProtocolNotFoundSemanticsTest,ProtocolLifecycleIntegrationTest,ProtocolErrorIntegrationTest test
```

Expected: PASS.

- [ ] **Step 4: Run full unit test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS with zero failures.

- [ ] **Step 5: Run full Maven verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS if Docker/Testcontainers is available. If Docker is unavailable, capture the exact Docker/Testcontainers failure and update `docs/code-quality-audit-status.md` with the reason instead of marking verify passed.

- [ ] **Step 6: Inspect git status and staged files**

Run:

```powershell
git status --short
```

Expected: only intended source, test, and doc changes are present. `docs/code-quality-audit-status.md` is currently untracked before implementation; after this task it should be staged intentionally because the implementation updates it.

- [ ] **Step 7: Commit docs and final verification updates**

Run:

```powershell
git add docs/API.md docs/code-quality-audit-status.md
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'docs: 更新消费链接口分页和404语义'
```

---

## Task 5: Final Review and Completion

**Files:**
- No planned code changes. Fix only issues found by verification or review.

- [ ] **Step 1: Verify commit authors**

Run:

```powershell
git log --format="%h %an <%ae> %s" -8
```

Expected: new implementation commits use author `GPT5.5XH <gpt5.5xh@example.local>` and Chinese commit messages.

- [ ] **Step 2: Confirm response contract with focused grep**

Run:

```powershell
rg -n "ResponseResult<List<ConsumeChainEdge>>|getConsumeChainEdgesById\\(UUID sourceId, UUID targetId, short currencyType, long startTime, long endTime\\)|getConsumeChainEdgesByPubkey\\(byte\\[] sourcePubkey, byte\\[] targetPubkey, short currencyType, long startTime, long endTime\\)" src/main/java docs/API.md
```

Expected: no matches. The endpoint and service should use paginated `Slice` / `SliceResponseDTO` signatures.

- [ ] **Step 3: Confirm 404 implementation with focused grep**

Run:

```powershell
rg -n "未冻结\"\\)\\)|未授权\"\\)\\)" src/main/java/com/cooperativesolutionism/nmsci/service
```

Expected: any query-time “未冻结/未授权” path in the three touched services uses `NotFoundException`, while write-path validators such as `FlowNodeStateValidator` may still use `IllegalArgumentException`.

- [ ] **Step 4: Run final test command**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS with zero failures. If `.\mvnw.cmd verify` passed in Task 4, do not rerun it here unless code changed after Task 4 Step 5.

- [ ] **Step 5: Final report**

Report:

```text
已完成：/consume-chains/edges 分页响应、size 上限、not-found 查询语义 404、API 文档和审计状态更新。
验证：列出 targeted tests、mvnw test、mvnw verify 的结果。
提交：列出本轮新增提交哈希和中文提交信息。
残余：源码哈希兜底、并发测试、Secp256k1 原语负路径测试仍按审计清单后续推进。
```
