# Conservative Quality Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the selected low-risk quality items without changing protocol behavior, database schema, or public API semantics.

**Architecture:** Keep behavior changes narrow and contract-driven. Make DTO JSON names explicit, move repository projections into a repository-owned package, compare consume-chain loop endpoints by persistent id, and document build/protocol invariants through focused tests and Maven cleanup.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Data JPA, Jackson, JUnit 5, Mockito, Maven, Testcontainers.

---

## File Structure

- `src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeStateResponseDTO.java` declares explicit Jackson names for boolean response fields.
- `src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeState.java` owns the aggregate state projection returned by `FlowNodeRegisterMsgRepository.findFlowNodeState`.
- `src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeStateOverview.java` owns the endpoint overview projection returned by `FlowNodeRegisterMsgRepository.findFlowNodeStateOverview`.
- `src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeState.java` is removed after imports move to the repository projection package.
- `src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateOverview.java` is removed after imports move to the repository projection package.
- `src/main/java/com/cooperativesolutionism/nmsci/repository/FlowNodeRegisterMsgRepository.java` imports the projections from `repository.projection`.
- `src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateValidator.java` imports `FlowNodeState` from `repository.projection`.
- `src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateEndpointTest.java` verifies DTO JSON names and the new projection package.
- `src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateValidatorTest.java` updates its local record to implement the moved projection interface.
- `src/main/java/com/cooperativesolutionism/nmsci/consume/LoopMarker.java` compares chain endpoints by persistent `FlowNodeRegisterMsg.id`.
- `src/test/java/com/cooperativesolutionism/nmsci/consume/LoopMarkerTest.java` covers same-id different-instance loop detection.
- `src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBaseTest.java` verifies Docker API version fallback behavior without requiring Docker.
- `src/test/java/com/cooperativesolutionism/nmsci/util/MerkleTreeUtilTest.java` documents odd-leaf duplicate-tail Merkle behavior.
- `src/test/java/com/cooperativesolutionism/nmsci/block/BlockGenerationLockTest.java` documents the transaction-scoped advisory lock SQL.
- `src/test/java/com/cooperativesolutionism/nmsci/block/BlockChainServiceLoopTest.java` remains the service-level lock-order contract.
- `src/test/java/com/cooperativesolutionism/nmsci/model/JpaEntityConservativeHardeningContractTest.java` documents that `@Version`, entity `equals/hashCode`, and byte-array id migrations are intentionally out of this conservative pass.
- `src/test/java/com/cooperativesolutionism/nmsci/model/BlockInfo.java` and `src/main/java/com/cooperativesolutionism/nmsci/model/MsgAbstract.java` remain unchanged except for optional test reflection coverage.
- `pom.xml` passes both `api.version` and `docker.api.version` to test forks and removes the empty Spring Boot `<requiresUnpack/>` entry after package verification.
- `docs/code-quality-audit-status.md` is not modified.

### Task 1: Flow Node State JSON Contract

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeStateResponseDTO.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateEndpointTest.java`

- [ ] **Step 1: Write the failing DTO contract test**

Add these imports to `FlowNodeStateEndpointTest.java`:

```java
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
```

Add this static import:

```java
import static org.junit.jupiter.api.Assertions.assertFalse;
```

Add this test method after `dtoMapsOverviewBooleans()`:

```java
    @Test
    void dtoDeclaresAndSerializesBooleanJsonContract() throws Exception {
        Class<?> dtoType = Class.forName(DTO_TYPE);
        Object dto = dtoType.getConstructor().newInstance();
        setBoolean(dto, "setRegistered", true);
        setBoolean(dto, "setAuthorized", false);
        setBoolean(dto, "setLocked", true);
        setBoolean(dto, "setCurrentCentralPubkeyAuthorized", false);

        assertJsonProperty(dtoType, "getRegistered", "registered");
        assertJsonProperty(dtoType, "getAuthorized", "authorized");
        assertJsonProperty(dtoType, "getLocked", "locked");
        assertJsonProperty(dtoType, "getCurrentCentralPubkeyAuthorized", "currentCentralPubkeyAuthorized");

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(dto));
        assertTrue(json.get("registered").asBoolean());
        assertFalse(json.get("authorized").asBoolean());
        assertTrue(json.get("locked").asBoolean());
        assertFalse(json.get("currentCentralPubkeyAuthorized").asBoolean());
        assertFalse(json.has("isLocked"));
        assertEquals(4, json.size());
    }
```

Add this helper before `setBoolean(...)`:

```java
    private void assertJsonProperty(Class<?> dtoType, String methodName, String expectedName) throws Exception {
        JsonProperty jsonProperty = dtoType.getMethod(methodName).getAnnotation(JsonProperty.class);
        assertEquals(expectedName, jsonProperty.value());
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=FlowNodeStateEndpointTest#dtoDeclaresAndSerializesBooleanJsonContract test
```

Expected: FAIL because `FlowNodeStateResponseDTO` getters do not yet declare `@JsonProperty`.

- [ ] **Step 3: Declare explicit Jackson field names**

Update `FlowNodeStateResponseDTO.java`:

```java
package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateOverview;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FlowNodeStateResponseDTO {

    private boolean registered;
    private boolean authorized;
    private boolean locked;
    private boolean currentCentralPubkeyAuthorized;

    public static FlowNodeStateResponseDTO from(FlowNodeStateOverview overview) {
        FlowNodeStateResponseDTO response = new FlowNodeStateResponseDTO();
        response.setRegistered(overview.getRegistered());
        response.setAuthorized(overview.getAuthorized());
        response.setLocked(overview.getLocked());
        response.setCurrentCentralPubkeyAuthorized(overview.getCurrentCentralPubkeyAuthorized());
        return response;
    }

    @JsonProperty("registered")
    public boolean getRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    @JsonProperty("authorized")
    public boolean getAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    @JsonProperty("locked")
    public boolean getLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @JsonProperty("currentCentralPubkeyAuthorized")
    public boolean getCurrentCentralPubkeyAuthorized() {
        return currentCentralPubkeyAuthorized;
    }

    public void setCurrentCentralPubkeyAuthorized(boolean currentCentralPubkeyAuthorized) {
        this.currentCentralPubkeyAuthorized = currentCentralPubkeyAuthorized;
    }
}
```

- [ ] **Step 4: Run the focused test and verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=FlowNodeStateEndpointTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Stage only the DTO and endpoint test:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeStateResponseDTO.java
git add src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateEndpointTest.java
git diff --cached --check
git diff --cached --name-only
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "fix: 固化流节点状态字段契约"
```

Expected staged files:

```text
src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeStateResponseDTO.java
src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateEndpointTest.java
```

### Task 2: Repository-Owned Flow Node State Projections

**Files:**
- Create: `src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeState.java`
- Create: `src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeStateOverview.java`
- Delete: `src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeState.java`
- Delete: `src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateOverview.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeStateResponseDTO.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/repository/FlowNodeRegisterMsgRepository.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateValidator.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateEndpointTest.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateValidatorTest.java`

- [ ] **Step 1: Update tests to expect repository projection ownership**

In `FlowNodeStateEndpointTest.java`, change the overview type constant:

```java
    private static final String OVERVIEW_TYPE = "com.cooperativesolutionism.nmsci.repository.projection.FlowNodeStateOverview";
```

In `FlowNodeStateValidatorTest.java`, add this import:

```java
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeState;
```

- [ ] **Step 2: Run the focused tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=FlowNodeStateEndpointTest,FlowNodeStateValidatorTest test
```

Expected: FAIL because the new projection package does not exist yet.

- [ ] **Step 3: Create the repository projection interfaces**

Create `src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeState.java`:

```java
package com.cooperativesolutionism.nmsci.repository.projection;

public interface FlowNodeState {

    boolean getRegistered();

    boolean getAuthorized();

    boolean getLocked();
}
```

Create `src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeStateOverview.java`:

```java
package com.cooperativesolutionism.nmsci.repository.projection;

public interface FlowNodeStateOverview {

    boolean getRegistered();

    boolean getAuthorized();

    boolean getLocked();

    boolean getCurrentCentralPubkeyAuthorized();
}
```

- [ ] **Step 4: Update production imports**

In `FlowNodeStateResponseDTO.java`, replace the projection import with:

```java
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeStateOverview;
```

In `FlowNodeRegisterMsgRepository.java`, replace the projection imports with:

```java
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeState;
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeStateOverview;
```

In `FlowNodeStateValidator.java`, add:

```java
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeState;
```

- [ ] **Step 5: Delete the old protocol projection interfaces**

Remove:

```text
src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeState.java
src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateOverview.java
```

- [ ] **Step 6: Verify there are no stale protocol projection references**

Run:

```powershell
rg -n "com\.cooperativesolutionism\.nmsci\.protocol\.FlowNodeState|com\.cooperativesolutionism\.nmsci\.protocol\.FlowNodeStateOverview|protocol\.FlowNodeState|protocol\.FlowNodeStateOverview" src/main src/test
```

Expected: no matches.

- [ ] **Step 7: Run focused tests**

Run:

```powershell
.\mvnw.cmd -Dtest=FlowNodeStateEndpointTest,FlowNodeStateValidatorTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeState.java
git add src/main/java/com/cooperativesolutionism/nmsci/repository/projection/FlowNodeStateOverview.java
git add src/main/java/com/cooperativesolutionism/nmsci/dto/FlowNodeStateResponseDTO.java
git add src/main/java/com/cooperativesolutionism/nmsci/repository/FlowNodeRegisterMsgRepository.java
git add src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateValidator.java
git add src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateEndpointTest.java
git add src/test/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateValidatorTest.java
git add -u src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeState.java
git add -u src/main/java/com/cooperativesolutionism/nmsci/protocol/FlowNodeStateOverview.java
git diff --cached --check
git diff --cached --name-only
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 收敛流节点状态投影包"
```

Expected: only the files listed in this task are staged.

### Task 3: Consume Chain Loop Detection by Persistent Id

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/consume/LoopMarker.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/consume/LoopMarkerTest.java`

- [ ] **Step 1: Write the failing same-id different-instance test**

Add this test to `LoopMarkerTest.java`:

```java
    @Test
    void samePersistentIdOnDifferentInstancesMarksChainAndEdgesAsLoop() {
        FlowNodeRegisterMsg start = node("11111111-1111-1111-1111-111111111111");
        FlowNodeRegisterMsg end = node("11111111-1111-1111-1111-111111111111");
        ConsumeChain chain = chain(start, end);
        ConsumeChainEdge edge = edge(chain);

        loopMarker.markChain(chain);
        loopMarker.markEdges(List.of(edge));

        assertTrue(chain.getIsLoop());
        assertTrue(edge.getIsLoop());
    }
```

Add this null-id defensive test:

```java
    @Test
    void missingPersistentIdsDoNotMarkDifferentInstancesAsLoop() {
        ConsumeChain chain = chain(new FlowNodeRegisterMsg(), new FlowNodeRegisterMsg());
        ConsumeChainEdge edge = edge(chain);

        loopMarker.markChain(chain);
        loopMarker.markEdges(List.of(edge));

        assertFalse(chain.getIsLoop());
        assertFalse(edge.getIsLoop());
    }
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=LoopMarkerTest test
```

Expected: FAIL on `samePersistentIdOnDifferentInstancesMarksChainAndEdgesAsLoop`.

- [ ] **Step 3: Compare loop endpoints by persisted id**

Update `LoopMarker.java`:

```java
package com.cooperativesolutionism.nmsci.consume;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class LoopMarker {

    public void markChain(ConsumeChain consumeChain) {
        consumeChain.setIsLoop(isLoop(consumeChain));
    }

    public void markEdges(List<ConsumeChainEdge> consumeChainEdges) {
        for (ConsumeChainEdge consumeChainEdge : consumeChainEdges) {
            consumeChainEdge.setIsLoop(isLoop(consumeChainEdge.getChain()));
        }
    }

    private boolean isLoop(ConsumeChain consumeChain) {
        return samePersistentFlowNode(consumeChain.getStart(), consumeChain.getEnd());
    }

    private boolean samePersistentFlowNode(FlowNodeRegisterMsg start, FlowNodeRegisterMsg end) {
        UUID startId = start.getId();
        return startId != null && startId.equals(end.getId());
    }
}
```

- [ ] **Step 4: Run the focused test**

Run:

```powershell
.\mvnw.cmd -Dtest=LoopMarkerTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/consume/LoopMarker.java
git add src/test/java/com/cooperativesolutionism/nmsci/consume/LoopMarkerTest.java
git diff --cached --check
git diff --cached --name-only
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "fix: 使用持久化标识判断消费链成环"
```

Expected staged files:

```text
src/main/java/com/cooperativesolutionism/nmsci/consume/LoopMarker.java
src/test/java/com/cooperativesolutionism/nmsci/consume/LoopMarkerTest.java
```

### Task 4: Docker API Version Fallback and Package Plugin Cleanup

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBase.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBaseTest.java`
- Modify: `pom.xml`

- [ ] **Step 1: Write fallback behavior tests**

Create `src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBaseTest.java`:

```java
package com.cooperativesolutionism.nmsci.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NmsciIntegrationTestBaseTest {

    private static final String API_VERSION_PROPERTY = "api.version";
    private static final String MAVEN_DOCKER_API_VERSION_PROPERTY = "docker.api.version";

    private final String originalApiVersion = System.getProperty(API_VERSION_PROPERTY);
    private final String originalMavenDockerApiVersion = System.getProperty(MAVEN_DOCKER_API_VERSION_PROPERTY);

    @AfterEach
    void restoreProperties() {
        restore(API_VERSION_PROPERTY, originalApiVersion);
        restore(MAVEN_DOCKER_API_VERSION_PROPERTY, originalMavenDockerApiVersion);
    }

    @Test
    void configuresDockerClientApiVersionFromMavenProperty() {
        System.clearProperty(API_VERSION_PROPERTY);
        System.setProperty(MAVEN_DOCKER_API_VERSION_PROPERTY, "1.41");

        NmsciIntegrationTestBase.configureDockerApiVersion();

        assertEquals("1.41", System.getProperty(API_VERSION_PROPERTY));
    }

    @Test
    void keepsExplicitDockerClientApiVersionOverride() {
        System.setProperty(API_VERSION_PROPERTY, "1.44");
        System.setProperty(MAVEN_DOCKER_API_VERSION_PROPERTY, "1.41");

        NmsciIntegrationTestBase.configureDockerApiVersion();

        assertEquals("1.44", System.getProperty(API_VERSION_PROPERTY));
    }

    @Test
    void configuresIdeFallbackWhenMavenPropertyIsMissing() {
        System.clearProperty(API_VERSION_PROPERTY);
        System.clearProperty(MAVEN_DOCKER_API_VERSION_PROPERTY);

        NmsciIntegrationTestBase.configureDockerApiVersion();

        assertEquals("1.40", System.getProperty(API_VERSION_PROPERTY));
    }

    private static void restore(String propertyName, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(propertyName);
            return;
        }

        System.setProperty(propertyName, originalValue);
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=NmsciIntegrationTestBaseTest test
```

Expected: FAIL because `configureDockerApiVersion()` is private and ignores `docker.api.version`.

- [ ] **Step 3: Tie fallback to the Maven property while preserving IDE fallback**

Update the constants and method in `NmsciIntegrationTestBase.java`:

```java
    private static final String DOCKER_API_VERSION_PROPERTY = "api.version";
    private static final String MAVEN_DOCKER_API_VERSION_PROPERTY = "docker.api.version";
    private static final String DEFAULT_DOCKER_API_VERSION = "1.40";
```

Replace the method with package-private visibility:

```java
    static void configureDockerApiVersion() {
        if (System.getProperty(DOCKER_API_VERSION_PROPERTY) == null) {
            System.setProperty(
                    DOCKER_API_VERSION_PROPERTY,
                    System.getProperty(MAVEN_DOCKER_API_VERSION_PROPERTY, DEFAULT_DOCKER_API_VERSION)
            );
        }
    }
```

- [ ] **Step 4: Pass the Maven property into forked test JVMs**

In both `maven-surefire-plugin` and `maven-failsafe-plugin` `<systemPropertyVariables>`, keep `api.version` and add `docker.api.version`:

```xml
                    <systemPropertyVariables>
                        <api.version>${docker.api.version}</api.version>
                        <docker.api.version>${docker.api.version}</docker.api.version>
                    </systemPropertyVariables>
```

- [ ] **Step 5: Remove the empty Spring Boot requiresUnpack entry**

Change the Spring Boot plugin configuration from:

```xml
                <configuration>
                    <attach>true</attach>
                    <requiresUnpack/>
                </configuration>
```

to:

```xml
                <configuration>
                    <attach>true</attach>
                </configuration>
```

- [ ] **Step 6: Run focused test and package verification**

Run:

```powershell
.\mvnw.cmd -Dtest=NmsciIntegrationTestBaseTest test
```

Expected: PASS.

Run:

```powershell
.\mvnw.cmd -DskipTests package
```

Expected: PASS, confirming the Spring Boot package plugin still works without the empty `<requiresUnpack/>`.

- [ ] **Step 7: Commit**

```powershell
git add pom.xml
git add src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBase.java
git add src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBaseTest.java
git diff --cached --check
git diff --cached --name-only
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "test: 固化测试构建配置契约"
```

Expected staged files:

```text
pom.xml
src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBase.java
src/test/java/com/cooperativesolutionism/nmsci/support/NmsciIntegrationTestBaseTest.java
```

### Task 5: Protocol and JPA Conservative Contracts

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/util/MerkleTreeUtilTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/block/BlockGenerationLockTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/model/JpaEntityConservativeHardeningContractTest.java`

- [ ] **Step 1: Add Merkle duplicate-tail contract coverage**

Append this test and helpers to `MerkleTreeUtilTest.java`:

```java
    @Test
    void duplicatesOddTailWhenBuildingMerkleLevels() {
        byte[] first = ByteArrayUtil.hexToBytes("1111111111111111111111111111111111111111111111111111111111111111");
        byte[] second = ByteArrayUtil.hexToBytes("2222222222222222222222222222222222222222222222222222222222222222");
        byte[] third = ByteArrayUtil.hexToBytes("3333333333333333333333333333333333333333333333333333333333333333");

        byte[] leftParent = merkleParent(first, second);
        byte[] duplicatedTailParent = merkleParent(third, third);
        byte[] expectedRoot = merkleParent(leftParent, duplicatedTailParent);

        assertArrayEquals(expectedRoot, MerkleTreeUtil.calcMerkleRoot(List.of(first, second, third)));
    }

    private static byte[] merkleParent(byte[] left, byte[] right) {
        byte[] combined = new byte[64];
        System.arraycopy(ByteArrayUtil.reverseBytes(left), 0, combined, 0, 32);
        System.arraycopy(ByteArrayUtil.reverseBytes(right), 0, combined, 32, 32);
        return ByteArrayUtil.reverseBytes(Sha256Util.doubleDigest(combined));
    }
```

- [ ] **Step 2: Add advisory lock SQL contract coverage**

Create `src/test/java/com/cooperativesolutionism/nmsci/block/BlockGenerationLockTest.java`:

```java
package com.cooperativesolutionism.nmsci.block;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockGenerationLockTest {

    @Test
    void usesTransactionScopedPostgresAdvisoryLock() {
        BlockGenerationLock lock = new BlockGenerationLock();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("select pg_advisory_xact_lock(:lockKey)")).thenReturn(query);
        when(query.setParameter("lockKey", 0x4E4D534349424C4BL)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);
        ReflectionTestUtils.setField(lock, "entityManager", entityManager);

        lock.lock();

        verify(entityManager).createNativeQuery("select pg_advisory_xact_lock(:lockKey)");
        verify(query).setParameter("lockKey", 0x4E4D534349424C4BL);
        verify(query).getSingleResult();
    }
}
```

- [ ] **Step 3: Add JPA conservative scope contract coverage**

Create `src/test/java/com/cooperativesolutionism/nmsci/model/JpaEntityConservativeHardeningContractTest.java`:

```java
package com.cooperativesolutionism.nmsci.model;

import jakarta.persistence.Id;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaEntityConservativeHardeningContractTest {

    @Test
    void byteArrayPrimaryKeysRemainUnchangedInThisConservativePass() throws Exception {
        Field blockInfoId = BlockInfo.class.getDeclaredField("id");
        Field msgAbstractId = MsgAbstract.class.getDeclaredField("id");

        assertEquals(byte[].class, blockInfoId.getType());
        assertEquals(byte[].class, msgAbstractId.getType());
        assertTrue(blockInfoId.isAnnotationPresent(Id.class));
        assertTrue(msgAbstractId.isAnnotationPresent(Id.class));
    }

    @Test
    void entitiesDoNotIntroduceVersionFieldsInThisConservativePass() {
        assertFalse(hasVersionField(BlockInfo.class));
        assertFalse(hasVersionField(MsgAbstract.class));
        assertFalse(hasVersionField(ConsumeChain.class));
        assertFalse(hasVersionField(FlowNodeRegisterMsg.class));
    }

    @Test
    void entitiesDoNotOverrideEqualsOrHashCodeInThisConservativePass() {
        assertFalse(overridesObjectMethod(BlockInfo.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(BlockInfo.class, "hashCode"));
        assertFalse(overridesObjectMethod(MsgAbstract.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(MsgAbstract.class, "hashCode"));
        assertFalse(overridesObjectMethod(ConsumeChain.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(ConsumeChain.class, "hashCode"));
        assertFalse(overridesObjectMethod(FlowNodeRegisterMsg.class, "equals", Object.class));
        assertFalse(overridesObjectMethod(FlowNodeRegisterMsg.class, "hashCode"));
    }

    private static boolean hasVersionField(Class<?> entityType) {
        return Arrays.stream(entityType.getDeclaredFields())
                .anyMatch(field -> field.isAnnotationPresent(Version.class));
    }

    private static boolean overridesObjectMethod(Class<?> entityType, String methodName, Class<?>... parameterTypes) {
        try {
            Method method = entityType.getDeclaredMethod(methodName, parameterTypes);
            return method.getDeclaringClass().equals(entityType);
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run focused contract tests**

Run:

```powershell
.\mvnw.cmd -Dtest=MerkleTreeUtilTest,BlockGenerationLockTest,JpaEntityConservativeHardeningContractTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add src/test/java/com/cooperativesolutionism/nmsci/util/MerkleTreeUtilTest.java
git add src/test/java/com/cooperativesolutionism/nmsci/block/BlockGenerationLockTest.java
git add src/test/java/com/cooperativesolutionism/nmsci/model/JpaEntityConservativeHardeningContractTest.java
git diff --cached --check
git diff --cached --name-only
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "test: 补充区块与实体保守契约"
```

Expected staged files:

```text
src/test/java/com/cooperativesolutionism/nmsci/block/BlockGenerationLockTest.java
src/test/java/com/cooperativesolutionism/nmsci/model/JpaEntityConservativeHardeningContractTest.java
src/test/java/com/cooperativesolutionism/nmsci/util/MerkleTreeUtilTest.java
```

### Task 6: Full Verification

**Files:**
- No source edits.

- [ ] **Step 1: Confirm audit status doc is untouched**

Run:

```powershell
git status --short -- docs/code-quality-audit-status.md
```

Expected: no output.

- [ ] **Step 2: Run unit tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS.

- [ ] **Step 3: Run full Maven verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS. If Docker/Testcontainers is unavailable, the integration test condition should skip the Testcontainers-backed tests instead of failing Maven.

- [ ] **Step 4: Verify package build**

Run:

```powershell
.\mvnw.cmd -DskipTests package
```

Expected: PASS.

- [ ] **Step 5: Attempt Docker build-stage verification**

Run:

```powershell
docker build --target build -t nmsci-build-check .
```

Expected: PASS when Docker Hub network access is available. If it fails while fetching Docker Hub auth/token or the Dockerfile frontend image before project build execution, record the exact external failure and do not mark Docker verification as passed.

- [ ] **Step 6: Final review**

Run:

```powershell
git status --short --branch
git log --format="%h %an <%ae> | %cn <%ce> | %s" -8
```

Expected: worktree clean except any intentionally uncommitted plan file if execution keeps the plan outside code commits; recent commits use `GPT5.5XH <gpt5.5xh@example.local>` and Chinese commit messages.

## Self-Review

- Spec coverage: Task 1 covers explicit boolean JSON names. Task 2 covers repository-owned projections. Task 3 covers conservative JPA loop comparison by persistent id. Task 4 covers Docker API fallback and empty `<requiresUnpack/>` removal. Task 5 covers Merkle duplicate-tail behavior, advisory lock invariant, and explicit conservative JPA non-changes. Task 6 covers full verification and the audit-status exclusion.
- Placeholder scan: The plan contains concrete files, code blocks, commands, and expected outcomes for each task.
- Type consistency: Projection names are `FlowNodeState` and `FlowNodeStateOverview` under `com.cooperativesolutionism.nmsci.repository.projection`; DTO getter names and JSON names match the current API contract.
