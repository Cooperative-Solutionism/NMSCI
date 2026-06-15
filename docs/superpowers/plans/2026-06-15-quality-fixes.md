# NMSCI Quality Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the approved high-priority code quality issues without hiding ordinary message `rawBytes` or expanding into medium-scope endpoint and error-contract rewrites.

**Architecture:** Keep the existing Spring Boot service/repository structure. Reuse `ProtocolRawBytesBuilder`, `SignatureValidator`, and `CentralSignatureService` for central pubkey lock signing, isolate graceful shutdown behind a testable interface, and keep long block-drain work outside the lock-message persistence transaction.

**Tech Stack:** Java 21, Spring Boot 3.5.3, Spring Data JPA, Micrometer, JUnit 5, Mockito, AssertJ, Maven, BouncyCastle `bcprov-jdk18on:1.84`.

---

## Scope Check

The approved spec touches several subsystems, but each change supports the same quality-fix objective. This stays as one implementation plan because each task is independently testable and the final result removes the frozen-key `System.exit(0)` risk plus low-cost defensive gaps.

## File Structure

- Modify `pom.xml`: upgrade `bouncycastle.version` from `1.78` to `1.84`.
- Modify `src/main/resources/application.properties`: set Flyway baseline to `0`, enable Hibernate schema validation, enable graceful shutdown.
- Modify `docs/API.md`: document that `BlockInfo.rawBytes` is hidden while ordinary message `rawBytes` remains output.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/concurrency/HibernateBatchConfigurationContractTest.java`: add configuration contract assertions.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/util/ByteArrayUtil.java`: add null validation and use `HexFormat`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/util/PoWUtil.java`: validate compact nBits exponent and mantissa.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java`: reject non-64-byte signatures before low-S parsing.
- Modify utility/protocol tests under `src/test/java/com/cooperativesolutionism/nmsci/util` and `src/test/java/com/cooperativesolutionism/nmsci/protocol`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/config/properties/NmsciProperties.java`: add central key pair match validation.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/config/properties/NmsciPropertiesValidationTest.java`: cover mismatched central key pair.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/protocol/BlockDifficultyService.java`: throw a clear conflict before genesis.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgService.java`: throw the same clear conflict before genesis.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/task/GenerateBlockTask.java`: add top-level scheduler error logging.
- Create `src/test/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgServiceTest.java`: cover pre-genesis registration.
- Create `src/test/java/com/cooperativesolutionism/nmsci/task/GenerateBlockTaskTest.java`: cover scheduler failure isolation.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/model/CentralPubkeyLockedMsg.java`: implement `CentrallySignedMessage`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java`: add frozen-key verify-data builder.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`: reuse protocol builders, use `TransactionTemplate`, request graceful shutdown through a service.
- Create `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockShutdownService.java`: shutdown request interface.
- Create `src/main/java/com/cooperativesolutionism/nmsci/service/SpringCentralPubkeyLockShutdownService.java`: production graceful shutdown implementation.
- Delete `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgPersistenceService.java`: obsolete transaction shim.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/support/ProtocolMessageBuilder.java`: add central pubkey lock test message builder.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilderTest.java`: cover frozen-key verify data.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/protocol/CentralSignatureServiceTest.java`: cover frozen-key raw bytes layout.
- Create `src/test/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgServiceTest.java`: cover save flow and shutdown request.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/concurrency/PersistenceConcurrencyContractTest.java`: replace deleted shim assertion with direct `TransactionTemplate` assertion.

### Task 1: Config, Dependency, and API Contract

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `docs/API.md`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/concurrency/HibernateBatchConfigurationContractTest.java`

- [ ] **Step 1: Add failing configuration assertions**

Add this test method to `HibernateBatchConfigurationContractTest`:

```java
@Test
void applicationPropertiesEnableMigrationValidationAndGracefulShutdown() throws IOException {
    String applicationProperties = Files.readString(Path.of("src/main/resources/application.properties"));

    assertTrue(applicationProperties.contains("spring.flyway.baseline-version=0"));
    assertTrue(applicationProperties.contains("spring.jpa.hibernate.ddl-auto=validate"));
    assertTrue(applicationProperties.contains("server.shutdown=graceful"));
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=HibernateBatchConfigurationContractTest test
```

Expected: FAIL because `application.properties` still contains `spring.flyway.baseline-version=1` and lacks the new schema/shutdown properties.

- [ ] **Step 3: Apply config and dependency changes**

In `pom.xml`, replace:

```xml
<bouncycastle.version>1.78</bouncycastle.version>
```

with:

```xml
<bouncycastle.version>1.84</bouncycastle.version>
```

In `src/main/resources/application.properties`, replace:

```properties
spring.flyway.baseline-version=1
```

with:

```properties
spring.flyway.baseline-version=0
```

Add these lines near the existing JPA and server settings:

```properties
spring.jpa.hibernate.ddl-auto=validate
server.shutdown=graceful
```

- [ ] **Step 4: Update API docs for rawBytes behavior**

In `docs/API.md`, update the shared-field section so it states:

```markdown
| `rawBytes`（普通消息原始字节缓存） | 输出（hex） | — | 普通消息 rawBytes 长度有限，作为协议调试与追溯字段保留输出。 |
```

Keep the block section wording that says `BlockInfo.rawBytes` is not output.

In the “所有消息实体” paragraph, replace the statement that all message `rawBytes` do not output with:

```markdown
所有消息实体：`id`(UUID)、`msgType`(数值类型码)、各公钥/签名为 hex、`txid`(hex)、`rawBytes`(hex)；可中心签名类型含 `confirmTimestamp`(微秒) 与 `centralSignature`(hex)。各类型专有字段：
```

- [ ] **Step 5: Run the targeted test and verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=HibernateBatchConfigurationContractTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

Run:

```powershell
git add pom.xml src/main/resources/application.properties docs/API.md src/test/java/com/cooperativesolutionism/nmsci/concurrency/HibernateBatchConfigurationContractTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'chore: 调整配置与依赖安全版本'
```

### Task 2: Primitive Defensive Validation

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/util/ByteArrayUtil.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/util/PoWUtil.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/util/ByteArrayUtilTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/util/PoWUtilTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidatorTest.java`

- [ ] **Step 1: Add failing utility and signature tests**

Add these imports and tests to `ByteArrayUtilTest`:

```java
@Test
void convertsBytesToLowercaseHexWithJdkHexFormat() {
    assertEquals("000fabff", ByteArrayUtil.bytesToHex(new byte[]{0x00, 0x0f, (byte) 0xab, (byte) 0xff}));
    assertEquals("", ByteArrayUtil.bytesToHex(new byte[0]));
}

@Test
void rejectsNullWhenConvertingBytesToHex() {
    IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ByteArrayUtil.bytesToHex(null)
    );

    assertEquals("字节数组不能为空", exception.getMessage());
}
```

Add these assertions to `PoWUtilTest.rejectsInvalidInputs()`:

```java
assertThrows(IllegalArgumentException.class, () -> PoWUtil.calculateTargetFromNBits(ByteArrayUtil.hexToBytes("02ffffff")));
assertThrows(IllegalArgumentException.class, () -> PoWUtil.calculateTargetFromNBits(ByteArrayUtil.hexToBytes("21ffffff")));
assertThrows(IllegalArgumentException.class, () -> PoWUtil.calculateTargetFromNBits(ByteArrayUtil.hexToBytes("20000000")));
```

Add this test to `SignatureValidatorTest`:

```java
@Test
void rejectsLowSValidationWhenSignatureIsNotRs64Bytes() {
    IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> signatureValidator.validateLowS(new byte[63], "签名不符合低S标准")
    );

    assertEquals("签名必须为64字节RS格式", exception.getMessage());
}
```

- [ ] **Step 2: Run targeted tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ByteArrayUtilTest,PoWUtilTest,SignatureValidatorTest test
```

Expected: FAIL because `bytesToHex(null)` throws a `NullPointerException`, invalid nBits inputs are not rejected consistently, and `validateLowS` does not check RS length first.

- [ ] **Step 3: Implement `ByteArrayUtil.bytesToHex`**

In `ByteArrayUtil.java`, add:

```java
import java.util.HexFormat;
```

Replace `bytesToHex` with:

```java
public static String bytesToHex(byte[] bytes) {
    if (bytes == null) {
        throw new IllegalArgumentException("字节数组不能为空");
    }
    return HexFormat.of().formatHex(bytes);
}
```

- [ ] **Step 4: Implement compact nBits validation**

Replace `calculateTargetFromNBits` in `PoWUtil.java` with:

```java
public static BigInteger calculateTargetFromNBits(byte[] nBits) {
    if (nBits == null || nBits.length != 4) {
        throw new IllegalArgumentException("nBits必须为4字节数组");
    }

    int exponent = nBits[0] & 0xFF;
    int mantissa = ((nBits[1] & 0xFF) << 16) | ((nBits[2] & 0xFF) << 8) | (nBits[3] & 0xFF);
    if (exponent < 3 || exponent > 32) {
        throw new IllegalArgumentException("nBits指数必须在3到32之间");
    }
    if (mantissa <= 0) {
        throw new IllegalArgumentException("nBits尾数必须为正数");
    }

    return BigInteger.valueOf(mantissa).multiply(BigInteger.valueOf(256).pow(exponent - 3));
}
```

- [ ] **Step 5: Implement low-S length validation**

Replace `validateLowS` in `SignatureValidator.java` with:

```java
public void validateLowS(byte[] signature, String errorMessage) {
    if (signature == null || signature.length != 64) {
        throw new IllegalArgumentException("签名必须为64字节RS格式");
    }
    try {
        if (Secp256k1EncryptUtil.isNotLowS(signature)) {
            throw new IllegalArgumentException(errorMessage);
        }
    } catch (IOException e) {
        throw new RuntimeException(e);
    }
}
```

- [ ] **Step 6: Run targeted tests and verify they pass**

Run:

```powershell
.\mvnw.cmd -Dtest=ByteArrayUtilTest,PoWUtilTest,SignatureValidatorTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/util/ByteArrayUtil.java src/main/java/com/cooperativesolutionism/nmsci/util/PoWUtil.java src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java src/test/java/com/cooperativesolutionism/nmsci/util/ByteArrayUtilTest.java src/test/java/com/cooperativesolutionism/nmsci/util/PoWUtilTest.java src/test/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidatorTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 加强协议原语边界校验'
```

### Task 3: Central Key Pair Validation

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/config/properties/NmsciProperties.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/config/properties/NmsciPropertiesValidationTest.java`

- [ ] **Step 1: Add failing mismatched-key test**

Add this test to `NmsciPropertiesValidationTest`:

```java
@Test
void failsFastWhenCentralPubkeyDoesNotMatchPrikey() {
    contextRunner
            .withPropertyValues("nmsci.central-key-pair.pubkey=" + TestKeyPairs.FLOW_NODE_A.pubkeyBase64())
            .run(context -> assertThat(context).hasFailed());
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=NmsciPropertiesValidationTest test
```

Expected: FAIL because mismatched but individually valid pubkey/prikey values still pass validation.

- [ ] **Step 3: Implement key pair match validation**

In `NmsciProperties.java`, add imports:

```java
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;

import java.util.Arrays;
```

Inside `CentralKeyPair`, add:

```java
@AssertTrue(message = "central-key-pair.pubkey与central-key-pair.prikey不匹配")
public boolean isKeyPairMatched() {
    byte[] decodedPubkey = decodeBase64(pubkey);
    byte[] decodedPrikey = decodeBase64(prikey);
    if (decodedPubkey == null || decodedPubkey.length != 33 || decodedPrikey == null || decodedPrikey.length != 32) {
        return true;
    }
    try {
        return Arrays.equals(decodedPubkey, Secp256k1EncryptUtil.rawToECKey(decodedPrikey).getPubKey());
    } catch (RuntimeException ex) {
        return false;
    }
}
```

- [ ] **Step 4: Run the targeted test and verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=NmsciPropertiesValidationTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/config/properties/NmsciProperties.java src/test/java/com/cooperativesolutionism/nmsci/config/properties/NmsciPropertiesValidationTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 校验中心密钥对一致性'
```

### Task 4: Pre-Genesis and Scheduler Failure Handling

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/protocol/BlockDifficultyService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgService.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/task/GenerateBlockTask.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/protocol/BlockDifficultyServiceTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgServiceTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/task/GenerateBlockTaskTest.java`

- [ ] **Step 1: Add failing pre-genesis difficulty test**

Add imports to `BlockDifficultyServiceTest`:

```java
import com.cooperativesolutionism.nmsci.exception.ConflictException;

import static org.junit.jupiter.api.Assertions.assertThrows;
```

Add this test:

```java
@Test
void rejectsTransactionDifficultyLookupBeforeGenesisBlockExists() {
    BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
    when(blockInfoRepository.findTopByOrderByHeightDesc()).thenReturn(null);
    BlockDifficultyService service = new BlockDifficultyService(blockInfoRepository, new SimpleMeterRegistry());

    ConflictException exception = assertThrows(ConflictException.class, service::currentTransactionDifficultyTarget);

    assertEquals("区块链尚未初始化，无法读取交易难度目标", exception.getMessage());
}
```

- [ ] **Step 2: Add failing pre-genesis registration test**

Create `FlowNodeRegisterMsgServiceTest.java`:

```java
package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowNodeRegisterMsgServiceTest {

    @Test
    void rejectsRegistrationBeforeGenesisBlockExists() {
        BlockInfoRepository blockInfoRepository = mock(BlockInfoRepository.class);
        when(blockInfoRepository.findTopByOrderByHeightDesc()).thenReturn(null);

        FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository = mock(FlowNodeRegisterMsgRepository.class);
        FlowNodeRegisterMsgService service = new FlowNodeRegisterMsgService();
        ReflectionTestUtils.setField(service, "blockInfoRepository", blockInfoRepository);
        ReflectionTestUtils.setField(service, "flowNodeRegisterMsgRepository", flowNodeRegisterMsgRepository);

        FlowNodeRegisterMsg msg = new FlowNodeRegisterMsg();
        msg.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msg.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        ConflictException exception = assertThrows(ConflictException.class, () -> service.saveFlowNodeRegisterMsg(msg));

        assertEquals("区块链尚未初始化，无法注册流转节点", exception.getMessage());
    }
}
```

- [ ] **Step 3: Add failing scheduler isolation test**

Create `GenerateBlockTaskTest.java`:

```java
package com.cooperativesolutionism.nmsci.task;

import com.cooperativesolutionism.nmsci.service.BlockChainService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GenerateBlockTaskTest {

    @Test
    void logsAndSuppressesBlockGenerationFailureForScheduler() {
        BlockChainService blockChainService = mock(BlockChainService.class);
        doThrow(new IllegalStateException("db unavailable")).when(blockChainService).generateBlock();

        GenerateBlockTask task = new GenerateBlockTask();
        ReflectionTestUtils.setField(task, "blockChainService", blockChainService);

        assertDoesNotThrow(task::execute);

        verify(blockChainService).generateBlock();
        verify(blockChainService, never()).generateBlockUntilNoNotInBlockMsgs();
    }
}
```

- [ ] **Step 4: Run targeted tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=BlockDifficultyServiceTest,FlowNodeRegisterMsgServiceTest,GenerateBlockTaskTest test
```

Expected: FAIL because the current code NPEs before genesis and the scheduler propagates `generateBlock()` exceptions.

- [ ] **Step 5: Implement pre-genesis checks**

In `BlockDifficultyService.java`, add:

```java
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
```

Replace `currentTransactionDifficultyTarget` with:

```java
public int currentTransactionDifficultyTarget() {
    return transactionDifficultyLookupTimer.record(() -> {
        BlockInfo latestBlock = blockInfoRepository.findTopByOrderByHeightDesc();
        if (latestBlock == null) {
            throw new ConflictException("区块链尚未初始化，无法读取交易难度目标");
        }
        return latestBlock.getTransactionDifficultyTarget();
    });
}
```

In `FlowNodeRegisterMsgService.java`, add:

```java
import com.cooperativesolutionism.nmsci.exception.ConflictException;
```

Immediately after:

```java
BlockInfo newestBlockInfo = blockInfoRepository.findTopByOrderByHeightDesc();
```

add:

```java
if (newestBlockInfo == null) {
    throw new ConflictException("区块链尚未初始化，无法注册流转节点");
}
```

- [ ] **Step 6: Implement scheduler error isolation**

Replace `execute()` in `GenerateBlockTask.java` with:

```java
@Scheduled(initialDelay = 0, fixedDelay = 10 * 60 * 1000)
public void execute() {
    Security.addProvider(new BouncyCastleProvider());

    long startTime = DateUtil.getCurrentMicros();
    logger.info("开始生成区块: {}", startTime);

    try {
        blockChainService.generateBlock();

        if (isFirstTimeRun) {
            blockChainService.generateBlockUntilNoNotInBlockMsgs();
            isFirstTimeRun = false;
            logger.info("第一次运行，已将所有未装块的消息都进行装块");
        }

        long endTime = DateUtil.getCurrentMicros();
        logger.info("成功生成区块: {}", endTime);
        logger.info("生成区块用时: {} 毫秒", (endTime - startTime) / 1000);
    } catch (Exception ex) {
        long endTime = DateUtil.getCurrentMicros();
        logger.error("生成区块失败，用时: {} 毫秒", (endTime - startTime) / 1000, ex);
    }
}
```

- [ ] **Step 7: Run targeted tests and verify they pass**

Run:

```powershell
.\mvnw.cmd -Dtest=BlockDifficultyServiceTest,FlowNodeRegisterMsgServiceTest,GenerateBlockTaskTest test
```

Expected: PASS.

- [ ] **Step 8: Commit**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/protocol/BlockDifficultyService.java src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgService.java src/main/java/com/cooperativesolutionism/nmsci/task/GenerateBlockTask.java src/test/java/com/cooperativesolutionism/nmsci/protocol/BlockDifficultyServiceTest.java src/test/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgServiceTest.java src/test/java/com/cooperativesolutionism/nmsci/task/GenerateBlockTaskTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 处理创世前请求和调度异常'
```

### Task 5: Central Pubkey Lock Refactor and Graceful Shutdown

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/model/CentralPubkeyLockedMsg.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`
- Create: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockShutdownService.java`
- Create: `src/main/java/com/cooperativesolutionism/nmsci/service/SpringCentralPubkeyLockShutdownService.java`
- Delete: `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgPersistenceService.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/support/ProtocolMessageBuilder.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilderTest.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/protocol/CentralSignatureServiceTest.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgServiceTest.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/concurrency/PersistenceConcurrencyContractTest.java`

- [ ] **Step 1: Add frozen-key protocol builder test**

In `ProtocolRawBytesBuilderTest.java`, add imports:

```java
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
```

Add this test:

```java
@Test
void buildsCentralPubkeyLockedVerifyDataInProtocolFieldOrder() {
    byte[] messageBytes = messageBuilder.centralPubkeyLocked(
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            TestKeyPairs.CENTRAL
    );

    assertArrayEquals(
            Arrays.copyOfRange(messageBytes, 0, 51),
            rawBytesBuilder.centralPubkeyLockedVerifyData(new CentralPubkeyLockedMsgConverter().fromByteArray(messageBytes))
    );
}
```

- [ ] **Step 2: Add frozen-key signing layout test**

In `CentralSignatureServiceTest.java`, add imports:

```java
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
```

Add this test:

```java
@Test
void signsCentralPubkeyLockedRawBytesInProtocolOrder() throws Exception {
    CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
            messageBuilder.centralPubkeyLocked(
                    UUID.fromString("77777777-7777-7777-7777-777777777777"),
                    TestKeyPairs.CENTRAL
            )
    );
    byte[] verifyData = rawBytesBuilder.centralPubkeyLockedVerifyData(msg);
    long timestamp = 987654321L;

    centralSignatureService.signAndPopulate(msg, verifyData, timestamp, msg.getCentralSignaturePre());

    assertEquals(timestamp, msg.getConfirmTimestamp());
    assertEquals(64, msg.getCentralSignature().length);
    assertEquals(32, msg.getTxid().length);
    assertEquals(verifyData.length + msg.getCentralSignaturePre().length + Long.BYTES + 64, msg.getRawBytes().length);
    assertTrue(Secp256k1EncryptUtil.verifySignature(
            rawBytesBuilder.centralSignData(verifyData, timestamp, msg.getCentralSignaturePre()),
            msg.getCentralSignature(),
            Secp256k1EncryptUtil.compressedToPublicKey(TestKeyPairs.CENTRAL.pubkey())
    ));
}
```

- [ ] **Step 3: Add frozen-key test message builder**

In `ProtocolMessageBuilder.java`, add this public method before `withMsgType`:

```java
public byte[] centralPubkeyLocked(UUID id, TestKeyPair central) {
    byte[] verifyData = ArrayUtils.addAll(
            ByteArrayUtil.shortToBytes(MsgTypeEnum.CentralPubkeyLockedMsg.getValue()),
            ByteArrayUtil.uuidToBytes(id)
    );
    verifyData = ArrayUtils.addAll(verifyData, central.pubkey());
    return ArrayUtils.addAll(verifyData, signRs(verifyData, central));
}
```

- [ ] **Step 4: Add service flow test**

Create `CentralPubkeyLockedMsgServiceTest.java`:

```java
package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.model.CentrallySignedMessage;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Consumer;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CentralPubkeyLockedMsgServiceTest {

    private final ProtocolMessageBuilder messageBuilder = new ProtocolMessageBuilder();

    @Test
    void savesLockMessageDrainsBlocksAndRequestsShutdownWithoutSystemExit() {
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyLocked(
                        UUID.fromString("88888888-8888-8888-8888-888888888888"),
                        TestKeyPairs.CENTRAL
                )
        );

        CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
        when(repository.existsById(msg.getId())).thenReturn(false);
        when(repository.existsByCentralPubkey(msg.getCentralPubkey())).thenReturn(false);
        when(repository.save(msg)).thenReturn(msg);

        MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
        BlockChainService blockChainService = mock(BlockChainService.class);
        CentralPubkeyLockShutdownService shutdownService = mock(CentralPubkeyLockShutdownService.class);
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
        ReflectionTestUtils.setField(service, "msgAbstractService", msgAbstractService);
        ReflectionTestUtils.setField(service, "blockChainService", blockChainService);
        ReflectionTestUtils.setField(service, "signatureValidator", new SignatureValidator());
        ReflectionTestUtils.setField(service, "protocolRawBytesBuilder", rawBytesBuilder);
        ReflectionTestUtils.setField(service, "centralSignatureService", new CentralSignatureService(properties(), rawBytesBuilder));
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(service, "shutdownService", shutdownService);

        service.saveCentralPubkeyLockedMsg(msg);

        assertNotNull(msg.getConfirmTimestamp());
        assertEquals(64, msg.getCentralSignature().length);
        assertEquals(187, msg.getRawBytes().length);
        assertEquals(32, msg.getTxid().length);
        verify(repository).save(msg);
        verify(msgAbstractService).saveMsgAbstract(msg);
        verify(blockChainService).generateBlockUntilNoNotInBlockMsgs();
        verify(shutdownService).requestShutdown();
    }

    @Test
    void centralPubkeyLockedMsgUsesCentrallySignedMessageContract() {
        assertInstanceOf(CentrallySignedMessage.class, new CentralPubkeyLockedMsg());
    }

    private NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }
}
```

- [ ] **Step 5: Update transaction contract test before implementation**

In `PersistenceConcurrencyContractTest.messageSavesThatPersistMsgAbstractRunInOneTransaction`, replace the `CentralPubkeyLockedMsgPersistenceService` assertion block with:

```java
assertNotNull(
        CentralPubkeyLockedMsgService.class.getDeclaredField("transactionTemplate"),
        "CentralPubkeyLockedMsgService must persist the lock message and msg_abstracts through TransactionTemplate"
);
```

Add import:

```java
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
```

Remove unused imports for `CentralPubkeyLockedMsg` and `Class.forName` usage if the compiler reports them unused.

- [ ] **Step 6: Run targeted tests and verify they fail**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolRawBytesBuilderTest,CentralSignatureServiceTest,CentralPubkeyLockedMsgServiceTest,PersistenceConcurrencyContractTest test
```

Expected: FAIL because the builder method, raw-bytes method, shutdown service, and service refactor do not exist yet.

- [ ] **Step 7: Implement `CentralPubkeyLockedMsg` contract**

In `CentralPubkeyLockedMsg.java`, replace:

```java
public class CentralPubkeyLockedMsg implements ConfirmableMessage {
```

with:

```java
public class CentralPubkeyLockedMsg implements CentrallySignedMessage {
```

- [ ] **Step 8: Implement frozen-key verify-data builder**

In `ProtocolRawBytesBuilder.java`, add import:

```java
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
```

Add constant near the other sizes:

```java
private static final int CENTRAL_PUBKEY_LOCKED_VERIFY_DATA_SIZE = Short.BYTES + UUID_BYTES + PUBKEY_BYTES;
```

Add method:

```java
public byte[] centralPubkeyLockedVerifyData(CentralPubkeyLockedMsg msg) {
    return ByteBuffer.allocate(CENTRAL_PUBKEY_LOCKED_VERIFY_DATA_SIZE)
            .putShort(msg.getMsgType())
            .put(uuidBytes(msg.getId()))
            .put(msg.getCentralPubkey())
            .array();
}
```

- [ ] **Step 9: Add shutdown service interface and implementation**

Create `CentralPubkeyLockShutdownService.java`:

```java
package com.cooperativesolutionism.nmsci.service;

public interface CentralPubkeyLockShutdownService {

    void requestShutdown();
}
```

Create `SpringCentralPubkeyLockShutdownService.java`:

```java
package com.cooperativesolutionism.nmsci.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SpringCentralPubkeyLockShutdownService implements CentralPubkeyLockShutdownService {

    private static final Logger logger = LoggerFactory.getLogger(SpringCentralPubkeyLockShutdownService.class);

    private final ConfigurableApplicationContext applicationContext;
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    public SpringCentralPubkeyLockShutdownService(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void requestShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) {
            logger.warn("中心公钥冻结停机请求已提交，忽略重复请求");
            return;
        }

        Thread shutdownThread = new Thread(
                () -> SpringApplication.exit(applicationContext, () -> 0),
                "central-pubkey-lock-shutdown"
        );
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }
}
```

- [ ] **Step 10: Refactor `CentralPubkeyLockedMsgService`**

Replace imports in `CentralPubkeyLockedMsgService.java` so it uses these protocol and transaction types:

```java
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import org.springframework.transaction.support.TransactionTemplate;
```

Remove imports for `DateUtil`, `MerkleTreeUtil`, `Secp256k1EncryptUtil`, `ArrayUtils`, `IOException`, and `Arrays` if they become unused.

Replace the old `centralPubkeyLockedMsgPersistenceService` field with:

```java
@Resource
private MsgAbstractService msgAbstractService;

@Resource
private SignatureValidator signatureValidator;

@Resource
private ProtocolRawBytesBuilder protocolRawBytesBuilder;

@Resource
private CentralSignatureService centralSignatureService;

@Resource
private TransactionTemplate transactionTemplate;

@Resource
private CentralPubkeyLockShutdownService shutdownService;
```

Replace the body of `saveCentralPubkeyLockedMsg` with:

```java
public void saveCentralPubkeyLockedMsg(@Valid @Nonnull CentralPubkeyLockedMsg centralPubkeyLockedMsg) {
    String centralPubkeyBase64 = nmsciProperties.getCentralPubkeyBase64();

    if (centralPubkeyLockedMsg.getMsgType() != MsgTypeEnum.CentralPubkeyLockedMsg.getValue()) {
        throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.CentralPubkeyLockedMsg.getValue());
    }

    if (centralPubkeyLockedMsgRepository.existsById(centralPubkeyLockedMsg.getId())) {
        throw new ConflictException("该中心公钥冻结信息id(" + centralPubkeyLockedMsg.getId() + ")已存在");
    }

    if (centralPubkeyLockedMsgRepository.existsByCentralPubkey(centralPubkeyLockedMsg.getCentralPubkey())) {
        throw new ConflictException("该中心公钥(" + ByteArrayUtil.bytesToBase64(centralPubkeyLockedMsg.getCentralPubkey()) + ")已被冻结");
    }

    byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
    if (!java.util.Arrays.equals(centralPubkeyLockedMsg.getCentralPubkey(), centralPubkey)) {
        throw new IllegalArgumentException("中心公钥设置错误，当前中心公钥为:(" + centralPubkeyBase64 + ")");
    }

    signatureValidator.validateLowS(centralPubkeyLockedMsg.getCentralSignaturePre(), "中心预签名不符合低S标准");

    byte[] verifyData = protocolRawBytesBuilder.centralPubkeyLockedVerifyData(centralPubkeyLockedMsg);
    signatureValidator.validateSignature(
            verifyData,
            centralPubkeyLockedMsg.getCentralSignaturePre(),
            centralPubkeyLockedMsg.getCentralPubkey(),
            "中心预签名验证失败"
    );
    centralSignatureService.signAndPopulate(
            centralPubkeyLockedMsg,
            verifyData,
            centralPubkeyLockedMsg.getCentralSignaturePre()
    );

    transactionTemplate.executeWithoutResult(status -> {
        centralPubkeyLockedMsgRepository.save(centralPubkeyLockedMsg);
        msgAbstractService.saveMsgAbstract(centralPubkeyLockedMsg);
    });

    blockChainService.generateBlockUntilNoNotInBlockMsgs();

    logger.warn("中心公钥冻结成功，所有未装块的信息装块成功，程序即将优雅终止");
    shutdownService.requestShutdown();
}
```

- [ ] **Step 11: Delete obsolete persistence shim**

Delete:

```text
src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgPersistenceService.java
```

- [ ] **Step 12: Run targeted tests and verify they pass**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolRawBytesBuilderTest,CentralSignatureServiceTest,CentralPubkeyLockedMsgServiceTest,PersistenceConcurrencyContractTest test
```

Expected: PASS.

- [ ] **Step 13: Commit**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/model/CentralPubkeyLockedMsg.java src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockShutdownService.java src/main/java/com/cooperativesolutionism/nmsci/service/SpringCentralPubkeyLockShutdownService.java src/test/java/com/cooperativesolutionism/nmsci/support/ProtocolMessageBuilder.java src/test/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilderTest.java src/test/java/com/cooperativesolutionism/nmsci/protocol/CentralSignatureServiceTest.java src/test/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgServiceTest.java src/test/java/com/cooperativesolutionism/nmsci/concurrency/PersistenceConcurrencyContractTest.java
git add -u src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgPersistenceService.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 重构中心公钥冻结停机流程'
```

### Task 6: Full Verification and Cleanup

**Files:**
- Modify: files changed by Tasks 1-5 only when tests reveal compile or behavior issues.

- [ ] **Step 1: Run unit tests**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS. If a compile error appears, fix the exact missing import, stale test assertion, or method signature mismatch and rerun `.\mvnw.cmd test`.

- [ ] **Step 2: Run full Maven verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS when Docker/Testcontainers is available. If Docker is unavailable, capture the exact failure text and still report that `.\mvnw.cmd test` passed.

- [ ] **Step 3: Inspect worktree**

Run:

```powershell
git status --short
```

Expected: no uncommitted files except Maven/Testcontainers generated files that are ignored by `.gitignore`.

- [ ] **Step 4: Confirm commit authors**

Run:

```powershell
git log --format='%h %an %s' -6
```

Expected: implementation commits show author name `GPT5.5XH` and Chinese commit messages.

- [ ] **Step 5: Final report**

Report:

```text
已完成：冻结流程移除 System.exit、配置与依赖升级、密钥对校验、创世前错误处理、调度异常隔离、原语防御、API 文档同步。
验证：列出 .\mvnw.cmd test 和 .\mvnw.cmd verify 的结果。
提交：列出本轮新增提交哈希和中文提交信息。
```
