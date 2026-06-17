# Remaining Quality Optimizations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the remaining current-state quality optimizations for freeze finalization, API contracts, read-only query boundaries, transaction-mount lock-window reduction, and protocol/build test hygiene.

**Architecture:** Keep each optimization in the narrowest existing boundary: service reliability stays in `CentralPubkeyLockedMsgService`, API semantics stay in controllers/serializers/DTOs, consume-chain query changes stay in service/repository, mount write locking moves into a focused transactional collaborator, and build hygiene stays in tests plus `pom.xml`. Each task starts with tests that fail against the current code and ends with a Chinese commit using `GPT5.5XH`.

**Tech Stack:** Java 21, Spring Boot MVC/Data JPA, JUnit 5, Mockito, MockMvc, Testcontainers integration tests, Maven Surefire/Failsafe.

---

## File Structure

- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`
  - Extract a finalization method that drains pending messages and requests shutdown even when drain fails.
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgServiceTest.java`
  - Add a drain-failure regression test.
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/serializer/BytesToHexSerializer.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/serializer/IntToHexSerializer.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/serializer/IdentifiableToStringSerializer.java`
  - Write JSON null when serializer input is null.
- Create: `src/test/java/com/cooperativesolutionism/nmsci/serializer/CustomSerializerNullSafetyTest.java`
  - Directly exercise custom serializers with null values.
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/dto/SliceResponseDTO.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/dto/LockedMessageResponseDTO.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeListItemDTO.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/dto/SystemStatusDTO.java`
  - Preserve boolean JSON field names with explicit `@JsonProperty`.
- Create: `src/test/java/com/cooperativesolutionism/nmsci/dto/BooleanDtoJsonContractTest.java`
  - Lock JSON field names for boolean DTO fields.
- Modify: six binary-write controllers:
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeRegisterMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyEmpowerMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyLockedMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeLockedMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionRecordMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionMountMsgController.java`
  - Add successful-create HTTP 201 status.
- Modify existing MockMvc integration tests that currently assert successful POSTs are `200 OK`.
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`
  - Add class-level read-only transaction.
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`
  - Split generic node-filter query into filter-specific methods.
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java`
  - Add read-only transaction contract.
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java`
  - Keep semantic coverage for START, END, NODE, and optional loop filters.
- Create: `src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountWriteService.java`
  - Own the transaction that locks the mounted record, saves the mount, and allocates consume chains.
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgService.java`
  - Move independent validation and signing before the transactional write collaborator.
- Create: `src/test/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgServiceTest.java`
  - Verify invalid pre-lock validation does not touch locked record lookup or allocation.
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/concurrency/PersistenceConcurrencyContractTest.java`
  - Move transaction-bound assertion from `TransactionMountMsgService` to `TransactionMountWriteService`.
- Create: `src/test/java/com/cooperativesolutionism/nmsci/protocol/ProtocolMessageCodecTest.java`
  - Cover registry completeness, duplicate converter detection, size mismatch detection, lookup, and decode.
- Modify: `pom.xml`
  - Replace duplicated Docker API version literals with one Maven property.

## Task 1: Freeze Drain Finalization

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgServiceTest.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`

- [ ] **Step 1: Write the failing drain-failure test**

Add this test to `CentralPubkeyLockedMsgServiceTest`:

```java
@Test
void requestsShutdownAndRethrowsWhenDrainFailsAfterLockMessageCommitted() {
    CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
            messageBuilder.centralPubkeyLocked(
                    UUID.fromString("99999999-9999-9999-9999-999999999999"),
                    TestKeyPairs.CENTRAL
            )
    );

    CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
    when(repository.existsById(msg.getId())).thenReturn(false);
    when(repository.existsByCentralPubkey(msg.getCentralPubkey())).thenReturn(false);

    MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
    BlockChainService blockChainService = mock(BlockChainService.class);
    CentralPubkeyLockShutdownService shutdownService = mock(CentralPubkeyLockShutdownService.class);
    RuntimeException drainFailure = new RuntimeException("drain failed");
    org.mockito.Mockito.doThrow(drainFailure)
            .when(blockChainService)
            .generateBlockUntilNoNotInBlockMsgs();

    TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    doAnswer(invocation -> {
        Consumer<TransactionStatus> action = invocation.getArgument(0);
        action.accept(new SimpleTransactionStatus());
        return null;
    }).when(transactionTemplate).executeWithoutResult(any());

    ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
    CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService();
    ReflectionTestUtils.setField(service, "nmsciProperties", properties());
    ReflectionTestUtils.setField(service, "centralPubkeyLockedMsgRepository", repository);
    ReflectionTestUtils.setField(service, "messageWritePipeline", new MessageWritePipeline(msgAbstractService));
    ReflectionTestUtils.setField(service, "blockChainService", blockChainService);
    ReflectionTestUtils.setField(service, "signatureValidator", new SignatureValidator());
    ReflectionTestUtils.setField(service, "protocolRawBytesBuilder", rawBytesBuilder);
    ReflectionTestUtils.setField(service, "centralSignatureService", new CentralSignatureService(properties(), rawBytesBuilder));
    ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
    ReflectionTestUtils.setField(service, "shutdownService", shutdownService);

    RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.saveCentralPubkeyLockedMsg(msg));

    assertEquals(drainFailure, thrown);
    verify(repository).save(msg);
    verify(msgAbstractService).saveMsgAbstract(msg);
    verify(blockChainService).generateBlockUntilNoNotInBlockMsgs();
    verify(shutdownService).requestShutdown();
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=CentralPubkeyLockedMsgServiceTest test
```

Expected RED: `requestsShutdownAndRethrowsWhenDrainFailsAfterLockMessageCommitted` fails because `shutdownService.requestShutdown()` is not called when `generateBlockUntilNoNotInBlockMsgs()` throws.

- [ ] **Step 3: Implement finalization with shutdown in `finally`**

In `CentralPubkeyLockedMsgService`, replace the direct drain/shutdown tail with:

```java
finalizeCentralPubkeyLock();
```

Add this public method to the service:

```java
public void finalizeCentralPubkeyLock() {
    try {
        blockChainService.generateBlockUntilNoNotInBlockMsgs();
        logger.warn("中心公钥冻结成功，所有未装块的信息装块成功，程序即将优雅终止");
    } catch (RuntimeException e) {
        logger.error("中心公钥冻结信息已提交，但未装块消息排空失败，程序仍将请求优雅终止", e);
        throw e;
    } finally {
        shutdownService.requestShutdown();
    }
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest=CentralPubkeyLockedMsgServiceTest test
```

Expected GREEN: 3 tests pass.

- [ ] **Step 5: Commit Task 1**

Run:

```powershell
git diff --check
git add src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java src/test/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgServiceTest.java docs/superpowers/plans/2026-06-17-remaining-quality-optimizations.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "fix: 收敛冻结排空失败处理"
```

## Task 2: API Contract Consistency

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/serializer/CustomSerializerNullSafetyTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/dto/BooleanDtoJsonContractTest.java`
- Modify custom serializers and boolean DTOs listed in the file structure.
- Modify six binary-write controllers.
- Modify MockMvc integration tests with successful POST status assertions.

- [ ] **Step 1: Write serializer null-safety tests**

Create `CustomSerializerNullSafetyTest.java`:

```java
package com.cooperativesolutionism.nmsci.serializer;

import com.cooperativesolutionism.nmsci.model.Identifiable;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class CustomSerializerNullSafetyTest {

    @Test
    void bytesSerializerWritesJsonNullForNullValue() throws Exception {
        JsonGenerator generator = mock(JsonGenerator.class);
        new BytesToHexSerializer().serialize(null, generator, mock(SerializerProvider.class));
        verify(generator).writeNull();
        verifyNoMoreInteractions(generator);
    }

    @Test
    void intSerializerWritesJsonNullForNullValue() throws Exception {
        JsonGenerator generator = mock(JsonGenerator.class);
        new IntToHexSerializer().serialize(null, generator, mock(SerializerProvider.class));
        verify(generator).writeNull();
        verifyNoMoreInteractions(generator);
    }

    @Test
    void identifiableSerializerWritesJsonNullForNullValue() throws Exception {
        JsonGenerator generator = mock(JsonGenerator.class);
        new IdentifiableToStringSerializer().serialize(null, generator, mock(SerializerProvider.class));
        verify(generator).writeNull();
        verifyNoMoreInteractions(generator);
    }
}
```

- [ ] **Step 2: Write boolean DTO JSON field-name tests**

Create `BooleanDtoJsonContractTest.java`:

```java
package com.cooperativesolutionism.nmsci.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanDtoJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sliceResponseKeepsBooleanFieldNames() throws Exception {
        SliceResponseDTO<String> dto = new SliceResponseDTO<>();
        dto.setContent(List.of("a"));
        dto.setPage(0);
        dto.setSize(1);
        dto.setNumberOfElements(1);
        dto.setHasNext(true);
        dto.setHasPrevious(false);

        JsonNode json = objectMapper.valueToTree(dto);

        assertTrue(json.get("hasNext").asBoolean());
        assertFalse(json.get("hasPrevious").asBoolean());
        assertFalse(json.has("next"));
        assertFalse(json.has("previous"));
    }

    @Test
    void lockedMessageResponseKeepsLockedFieldName() {
        LockedMessageResponseDTO<Object> dto = new LockedMessageResponseDTO<>(true, null);

        JsonNode json = objectMapper.valueToTree(dto);

        assertTrue(json.get("locked").asBoolean());
        assertFalse(json.has("isLocked"));
    }

    @Test
    void flowNodeListItemKeepsBooleanFieldNames() {
        FlowNodeListItemDTO dto = new FlowNodeListItemDTO(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                new byte[33],
                true,
                false,
                true,
                false
        );

        JsonNode json = objectMapper.valueToTree(dto);

        assertTrue(json.get("registered").asBoolean());
        assertFalse(json.get("authorized").asBoolean());
        assertTrue(json.get("locked").asBoolean());
        assertFalse(json.get("currentCentralPubkeyAuthorized").asBoolean());
    }

    @Test
    void systemStatusKeepsCurrentCentralPubkeyLockedFieldName() {
        SystemStatusDTO dto = new SystemStatusDTO();
        dto.setCurrentCentralPubkeyLocked(true);

        JsonNode json = objectMapper.valueToTree(dto);

        assertTrue(json.get("currentCentralPubkeyLocked").asBoolean());
        assertFalse(json.has("isCurrentCentralPubkeyLocked"));
    }
}
```

- [ ] **Step 3: Write POST 201 integration expectations**

Update successful POST assertions in these files from `status().isOk()` to `status().isCreated()` for binary create endpoints only:

```text
src/test/java/com/cooperativesolutionism/nmsci/integration/BlockChainIntegrationTest.java
src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolErrorIntegrationTest.java
src/test/java/com/cooperativesolutionism/nmsci/integration/ProtocolLifecycleIntegrationTest.java
```

Do not change GET expectations. Do not change failing POST expectations such as 400 or 409.

- [ ] **Step 4: Run API-focused tests and verify RED**

Run:

```powershell
.\mvnw.cmd "-Dtest=CustomSerializerNullSafetyTest,BooleanDtoJsonContractTest" test
.\mvnw.cmd "-Dit.test=ProtocolErrorIntegrationTest,ProtocolLifecycleIntegrationTest,BlockChainIntegrationTest" test-compile failsafe:integration-test failsafe:verify
```

Expected RED:

- Serializer null-safety tests fail until serializers call `writeNull()`.
- POST integration tests expecting 201 fail with 200 until controllers declare `CREATED`.

- [ ] **Step 5: Implement serializer and DTO annotations**

For each serializer, add a null guard:

```java
if (value == null) {
    gen.writeNull();
    return;
}
```

For boolean DTO getters that use non-standard names or could serialize ambiguously, add explicit field annotations:

```java
@com.fasterxml.jackson.annotation.JsonProperty("hasNext")
public boolean getHasNext() {
    return hasNext;
}
```

Use the same pattern for `hasPrevious`, `locked`, `registered`, `authorized`, `currentCentralPubkeyAuthorized`, and `currentCentralPubkeyLocked`.

- [ ] **Step 6: Implement 201 for create endpoints**

In each binary POST controller method, add:

```java
@ResponseStatus(HttpStatus.CREATED)
```

and import:

```java
import org.springframework.http.HttpStatus;
```

Keep return bodies as `ResponseResult.success(...)`.

- [ ] **Step 7: Run focused and integration tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=CustomSerializerNullSafetyTest,BooleanDtoJsonContractTest,BinaryWriteEndpointContractTest,ResponseResultTest" test
.\mvnw.cmd "-Dit.test=ProtocolErrorIntegrationTest,ProtocolLifecycleIntegrationTest,BlockChainIntegrationTest" test-compile failsafe:integration-test failsafe:verify
```

Expected GREEN: serializer/DTO unit tests pass, successful create endpoints return 201, failing request paths still return their existing errors.

- [ ] **Step 8: Commit Task 2**

Run:

```powershell
git diff --check
git add src/main/java/com/cooperativesolutionism/nmsci/serializer src/main/java/com/cooperativesolutionism/nmsci/dto src/main/java/com/cooperativesolutionism/nmsci/controller src/test/java/com/cooperativesolutionism/nmsci/serializer src/test/java/com/cooperativesolutionism/nmsci/dto src/test/java/com/cooperativesolutionism/nmsci/integration
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "fix: 统一创建响应契约"
```

## Task 3: Consume-Chain Read-Only Query Boundaries

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java`
- Re-run: `src/test/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainRepositoryNodeFilterTest.java`

- [ ] **Step 1: Write read-only transaction contract**

Add this test to `ConsumeChainQueryServiceTest`:

```java
@Test
void queryServiceDeclaresReadOnlyTransactionsAtClassBoundary() {
    org.springframework.transaction.annotation.Transactional transactional =
            ConsumeChainQueryService.class.getAnnotation(org.springframework.transaction.annotation.Transactional.class);

    assertNotNull(transactional);
    assertTrue(transactional.readOnly());
}
```

- [ ] **Step 2: Run focused unit test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=ConsumeChainQueryServiceTest test
```

Expected RED: the new test fails because `ConsumeChainQueryService` has no class-level `@Transactional(readOnly = true)`.

- [ ] **Step 3: Add read-only transaction boundary**

Add this import and annotation to `ConsumeChainQueryService`:

```java
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsumeChainQueryService {
```

- [ ] **Step 4: Split repository query by filter**

Change the repository default method to dispatch by filter:

```java
return switch (filter) {
    case START -> findByStartAndOptionalLoop(node, isLoop, pageable);
    case END -> findByEndAndOptionalLoop(node, isLoop, pageable);
    case NODE -> findByNodeAndOptionalLoop(node, isLoop, pageable);
};
```

Replace `findByNodeFilterInternal` with three focused query methods:

```java
@Query("""
        select c
        from ConsumeChain c
        where (:isLoop is null or c.isLoop = :isLoop)
            and c.start = :node
        """)
Slice<ConsumeChain> findByStartAndOptionalLoop(
        @Param("node") FlowNodeRegisterMsg node,
        @Param("isLoop") Boolean isLoop,
        Pageable pageable
);

@Query("""
        select c
        from ConsumeChain c
        where (:isLoop is null or c.isLoop = :isLoop)
            and c.end = :node
        """)
Slice<ConsumeChain> findByEndAndOptionalLoop(
        @Param("node") FlowNodeRegisterMsg node,
        @Param("isLoop") Boolean isLoop,
        Pageable pageable
);

@Query("""
        select distinct c
        from ConsumeChain c
        where (:isLoop is null or c.isLoop = :isLoop)
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
Slice<ConsumeChain> findByNodeAndOptionalLoop(
        @Param("node") FlowNodeRegisterMsg node,
        @Param("isLoop") Boolean isLoop,
        Pageable pageable
);
```

- [ ] **Step 5: Run query tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=ConsumeChainQueryServiceTest" test
.\mvnw.cmd "-Dtest=ConsumeChainRepositoryNodeFilterTest" "-Dit.test=ConsumeChainRepositoryNodeFilterTest" verify
```

Expected GREEN: unit tests pass; repository integration test still returns the same ids for START, END, NODE, and optional loop filters.

- [ ] **Step 6: Commit Task 3**

Run:

```powershell
git diff --check
git add src/main/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryService.java src/main/java/com/cooperativesolutionism/nmsci/repository/ConsumeChainRepository.java src/test/java/com/cooperativesolutionism/nmsci/service/ConsumeChainQueryServiceTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "perf: 收敛消费链只读查询边界"
```

## Task 4: Transaction-Mount Validation Before Lock-Sensitive Write

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgServiceTest.java`
- Create: `src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountWriteService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgService.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/concurrency/PersistenceConcurrencyContractTest.java`

- [ ] **Step 1: Write pre-lock validation ordering test**

Create `TransactionMountMsgServiceTest.java` with this test skeleton and helper fields:

```java
package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.protocol.BlockDifficultyService;
import com.cooperativesolutionism.nmsci.protocol.CentralPubkeyValidator;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateValidator;
import com.cooperativesolutionism.nmsci.protocol.ProofOfWorkValidator;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionMountMsgServiceTest {

    @Test
    void lowSFailureHappensBeforeTransactionalWriteAndAllocation() {
        TransactionMountMsg mount = mount();
        TransactionMountMsgRepository repository = mock(TransactionMountMsgRepository.class);
        when(repository.existsById(mount.getId())).thenReturn(false);

        BlockDifficultyService blockDifficultyService = mock(BlockDifficultyService.class);
        when(blockDifficultyService.currentTransactionDifficultyTarget()).thenReturn(0x1f00ffff);

        SignatureValidator signatureValidator = mock(SignatureValidator.class);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("消费节点签名不符合低S值要求"))
                .when(signatureValidator)
                .validateLowS(mount.getConsumeNodeSignature(), "消费节点签名不符合低S值要求");

        TransactionMountWriteService writeService = mock(TransactionMountWriteService.class);

        TransactionMountMsgService service = new TransactionMountMsgService();
        ReflectionTestUtils.setField(service, "blockDifficultyService", blockDifficultyService);
        ReflectionTestUtils.setField(service, "transactionMountMsgRepository", repository);
        ReflectionTestUtils.setField(service, "messageWritePipeline", mock(MessageWritePipeline.class));
        ReflectionTestUtils.setField(service, "flowNodeStateValidator", mock(FlowNodeStateValidator.class));
        ReflectionTestUtils.setField(service, "centralPubkeyValidator", mock(CentralPubkeyValidator.class));
        ReflectionTestUtils.setField(service, "signatureValidator", signatureValidator);
        ReflectionTestUtils.setField(service, "proofOfWorkValidator", mock(ProofOfWorkValidator.class));
        ReflectionTestUtils.setField(service, "protocolRawBytesBuilder", mock(ProtocolRawBytesBuilder.class));
        ReflectionTestUtils.setField(service, "centralSignatureService", mock(CentralSignatureService.class));
        ReflectionTestUtils.setField(service, "transactionMountWriteService", writeService);

        assertThrows(IllegalArgumentException.class, () -> service.saveTransactionMountMsg(mount));

        verify(writeService, never()).saveAndAllocate(any());
    }

    private TransactionMountMsg mount() {
        TransactionMountMsg mount = new TransactionMountMsg();
        mount.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        mount.setMsgType(MsgTypeEnum.TransactionMountMsg.getValue());
        mount.setMountedTransactionRecordId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        mount.setTransactionDifficultyTarget(0x1f00ffff);
        mount.setConsumeNodePubkey(new byte[33]);
        mount.setFlowNodePubkey(new byte[33]);
        mount.setCentralPubkey(new byte[33]);
        mount.setConsumeNodeSignature(new byte[64]);
        mount.setFlowNodeSignature(new byte[64]);
        return mount;
    }
}
```

- [ ] **Step 2: Update concurrency contract test expectation**

In `PersistenceConcurrencyContractTest`, replace the assertion for `TransactionMountMsgService.saveTransactionMountMsg` with:

```java
assertTransactional(
        TransactionMountWriteService.class,
        "saveAndAllocate",
        TransactionMountMsg.class
);
```

Replace the current one-parameter helper with this varargs helper:

```java
private void assertTransactional(Class<?> serviceClass, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
    Method method = serviceClass.getMethod(methodName, parameterTypes);
    assertNotNull(
            method.getAnnotation(Transactional.class),
            serviceClass.getSimpleName() + "." + methodName + " must run persistence and allocation in one transaction"
    );
}
```

- [ ] **Step 3: Run focused tests and verify RED**

Run:

```powershell
.\mvnw.cmd "-Dtest=TransactionMountMsgServiceTest,PersistenceConcurrencyContractTest" test
```

Expected RED: `TransactionMountWriteService` does not exist and the new service test cannot inject it.

- [ ] **Step 4: Create transactional write collaborator**

Create `TransactionMountWriteService`:

```java
package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
public class TransactionMountWriteService {

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    @Resource
    private MessageWritePipeline messageWritePipeline;

    @Resource
    private ConsumeChainAllocationService consumeChainAllocationService;

    @Transactional
    public TransactionMountMsg saveAndAllocate(TransactionMountMsg transactionMountMsg) {
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgRepository.findByIdForUpdate(
                transactionMountMsg.getMountedTransactionRecordId()
        ).orElseThrow(() -> new IllegalArgumentException(
                "挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")不存在"
        ));

        if (transactionMountMsgRepository.existsTransactionMountMsgByMountedTransactionRecordId(
                transactionMountMsg.getMountedTransactionRecordId())) {
            throw new ConflictException("挂载的交易记录信息id(" + transactionMountMsg.getMountedTransactionRecordId() + ")已被挂载");
        }

        byte[] consumeNodePubkey = transactionRecordMsg.getConsumeNodePubkey();
        if (!Arrays.equals(transactionMountMsg.getConsumeNodePubkey(), consumeNodePubkey)) {
            String consumeNodePubkeyBase64 = ByteArrayUtil.bytesToBase64(consumeNodePubkey);
            throw new IllegalArgumentException("挂载的交易记录信息中的消费节点公钥(" + consumeNodePubkeyBase64 + ")与当前交易挂载信息中的消费节点公钥不一致");
        }

        TransactionMountMsg transactionMountMsgInDb = messageWritePipeline.saveEntityThenAbstract(
                transactionMountMsg,
                transactionMountMsgRepository::save
        );
        consumeChainAllocationService.saveConsumeChain(transactionMountMsgInDb, transactionRecordMsg);
        return transactionMountMsgInDb;
    }
}
```

- [ ] **Step 5: Refactor `TransactionMountMsgService` outer validation**

Inject:

```java
@Resource
private TransactionMountWriteService transactionMountWriteService;
```

Remove `@Transactional` from `saveTransactionMountMsg`. Keep:

- message type check
- duplicate id preflight check
- difficulty lookup
- flow node state validation
- central key validation
- low-S validation
- verify-data building
- PoW validation
- signature validation
- central signing

Replace the final save/allocation block with:

```java
return transactionMountWriteService.saveAndAllocate(transactionMountMsg);
```

- [ ] **Step 6: Run focused and concurrency tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=TransactionMountMsgServiceTest,PersistenceConcurrencyContractTest,ProtocolValidationOptimizationContractTest" test
.\mvnw.cmd "-Dit.test=ConsumeChainAllocationConcurrencyIntegrationTest" verify
```

Expected GREEN: pre-lock validation failure never calls `TransactionMountWriteService`; transactional write contract points at the new collaborator; real two-thread allocation test still passes.

- [ ] **Step 7: Commit Task 4**

Run:

```powershell
git diff --check
git add src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountWriteService.java src/test/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgServiceTest.java src/test/java/com/cooperativesolutionism/nmsci/concurrency/PersistenceConcurrencyContractTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "perf: 缩短交易挂载锁窗口"
```

## Task 5: Protocol Codec and Build Hygiene

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/protocol/ProtocolMessageCodecTest.java`
- Modify: `pom.xml`

- [ ] **Step 1: Write protocol codec tests**

Create `ProtocolMessageCodecTest.java`:

```java
package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.MessageConverter;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolMessageCodecTest {

    @Test
    void constructorRejectsDuplicateConvertersForSameMessageType() {
        MessageConverter<Message> first = converter(MsgTypeEnum.FlowNodeRegisterMsg, MsgTypeEnum.FlowNodeRegisterMsg.getInboundSize(), new StubMessage());
        MessageConverter<Message> second = converter(MsgTypeEnum.FlowNodeRegisterMsg, MsgTypeEnum.FlowNodeRegisterMsg.getInboundSize(), new StubMessage());
        List<MessageConverter<?>> converters = completeConvertersExcept(MsgTypeEnum.FlowNodeRegisterMsg);
        converters.add(first);
        converters.add(second);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new ProtocolMessageCodec(converters));

        assertEquals("消息类型 FlowNodeRegisterMsg 注册了多个转换器", thrown.getMessage());
    }

    @Test
    void constructorRejectsMissingConverter() {
        List<MessageConverter<?>> converters = completeConvertersExcept(MsgTypeEnum.TransactionMountMsg);

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new ProtocolMessageCodec(converters));

        assertEquals("消息类型 TransactionMountMsg 缺少转换器", thrown.getMessage());
    }

    @Test
    void constructorRejectsConverterSizeMismatch() {
        List<MessageConverter<?>> converters = completeConvertersExcept(MsgTypeEnum.TransactionRecordMsg);
        converters.add(converter(MsgTypeEnum.TransactionRecordMsg, MsgTypeEnum.TransactionRecordMsg.getInboundSize() + 1, new StubMessage()));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> new ProtocolMessageCodec(converters));

        assertEquals(
                "消息类型 TransactionRecordMsg 的转换器入站字节数("
                        + (MsgTypeEnum.TransactionRecordMsg.getInboundSize() + 1)
                        + ")与枚举声明的入站字节数("
                        + MsgTypeEnum.TransactionRecordMsg.getInboundSize()
                        + ")不一致",
                thrown.getMessage()
        );
    }

    @Test
    void decodeUsesConverterForMessageType() {
        StubMessage decoded = new StubMessage();
        ProtocolMessageCodec codec = new ProtocolMessageCodec(completeConvertersWith(
                MsgTypeEnum.TransactionMountMsg,
                converter(MsgTypeEnum.TransactionMountMsg, MsgTypeEnum.TransactionMountMsg.getInboundSize(), decoded)
        ));

        Message result = codec.decode(MsgTypeEnum.TransactionMountMsg, new byte[] {1, 2, 3});

        assertSame(decoded, result);
    }

    private List<MessageConverter<?>> completeConvertersExcept(MsgTypeEnum excluded) {
        List<MessageConverter<?>> converters = new ArrayList<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            if (msgType != excluded) {
                converters.add(converter(msgType, msgType.getInboundSize(), new StubMessage()));
            }
        }
        return converters;
    }

    private List<MessageConverter<?>> completeConvertersWith(MsgTypeEnum replacementType, MessageConverter<?> replacement) {
        List<MessageConverter<?>> converters = completeConvertersExcept(replacementType);
        converters.add(replacement);
        return converters;
    }

    private MessageConverter<Message> converter(MsgTypeEnum msgType, int expectedSize, Message decoded) {
        return new MessageConverter<>() {
            @Override
            public MsgTypeEnum msgType() {
                return msgType;
            }

            @Override
            public int expectedSize() {
                return expectedSize;
            }

            @Override
            public Message fromByteArray(byte[] byteData) {
                return decoded;
            }
        };
    }

    private static class StubMessage implements Message {
        @Override
        public Short getMsgType() {
            return 0;
        }

        @Override
        public byte[] getRawBytes() {
            return new byte[0];
        }

        @Override
        public byte[] getTxid() {
            return new byte[0];
        }
    }
}
```

- [ ] **Step 2: Run protocol codec coverage test**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolMessageCodecTest test
```

Expected: the test may already pass because `ProtocolMessageCodec` currently performs these checks. If it passes, keep the test as coverage and do not change production codec code.

- [ ] **Step 3: De-duplicate Docker API version property**

Add one Maven property:

```xml
<docker.api.version>1.40</docker.api.version>
```

Replace both Surefire and Failsafe system property values:

```xml
<api.version>${docker.api.version}</api.version>
```

If a third literal is found in another plugin block, replace it with the same property. Do not change plugin behavior.

- [ ] **Step 4: Run focused build-hygiene tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=ProtocolMessageCodecTest" test
```

Then run a Maven test command that exercises Surefire property interpolation:

```powershell
.\mvnw.cmd "-Dtest=ResponseResultTest" test
```

Expected GREEN: tests compile and pass.

- [ ] **Step 5: Commit Task 5**

Run:

```powershell
git diff --check
git add pom.xml src/test/java/com/cooperativesolutionism/nmsci/protocol/ProtocolMessageCodecTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "test: 补充协议编解码契约"
```

## Task 6: Final Verification

**Files:**
- No production edits unless verification exposes a concrete defect.
- Do not modify `docs/code-quality-audit-status.md`.

- [ ] **Step 1: Run full unit tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected GREEN: Surefire exits 0. Record the test count.

- [ ] **Step 2: Run full verification if Docker/Testcontainers are available**

Run:

```powershell
.\mvnw.cmd verify
```

Expected GREEN when Docker/Testcontainers are available. If Docker Hub or Docker daemon/network access fails, capture the exact failing lines and report the external failure without claiming full verify passed.

- [ ] **Step 3: Attempt Docker build-stage verification**

Run:

```powershell
docker build --target build -t nmsci-source-hash-build-test .
```

Expected GREEN only if Docker can pull base images and fetch registry tokens. If the same Docker Hub token/network failure appears, report the exact failure and leave this verification as externally blocked.

- [ ] **Step 4: Check git state and recent commits**

Run:

```powershell
git status --short --branch
git log --format="%h %an <%ae> | %cn <%ce> | %s" -8
git diff --check
```

Expected: no unstaged/uncommitted code changes except intentionally uncommitted verification artifacts; commit author and committer are `GPT5.5XH <gpt5.5xh@example.local>`.

- [ ] **Step 5: Final response**

Report:

- Each batch commit hash and Chinese message.
- Tests run and actual pass/fail counts.
- Whether Docker build-stage was verified or externally blocked.
- Confirmation that `docs/code-quality-audit-status.md` was not modified.
