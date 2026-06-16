# Protocol Byte Length Constants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace duplicated protocol byte-length literals with shared compile-time constants without changing protocol bytes or validation behavior.

**Architecture:** Add one constants class in the existing `com.cooperativesolutionism.nmsci.constant` package. Move message-level inbound/stored lengths and protocol field byte lengths to that class, then replace production references in controllers, converters, enums, models, validators, services, config validation, crypto helpers, and raw-byte building. Use focused contract tests plus source scans to guard that annotation and converter hardcoding is removed while deliberately leaving unrelated hash/Merkle/test literals alone.

**Tech Stack:** Java 21, Spring MVC, Jakarta Bean Validation annotations, JUnit 5, Maven Surefire/Failsafe.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-16-protocol-byte-lengths-design.md`.

In scope:

- Add `ProtocolByteLengths` with compile-time `public static final int` constants.
- Cover message inbound/stored byte lengths.
- Cover UUID, compressed public key, RS signature, and raw private key lengths.
- Replace production literals where they clearly represent those protocol lengths.
- Add focused contract tests for enum sizes, converter sizes, and controller `@ByteArraySize` metadata.
- Update audit status after verification.

Out of scope:

- Converter byte-slice offset refactors.
- Merkle/hash length constants.
- Test data literals that intentionally construct invalid lengths.
- `ByteArraySize` annotation redesign.
- API `consumes` changes.
- Global exception mapping changes.
- Query-service or write-service template refactors.

## File Structure

- Create `src/main/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengths.java`: compile-time protocol length constants.
- Create `src/test/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengthsContractTest.java`: enum/converter/controller annotation contract coverage.
- Modify message length consumers:
  - `src/main/java/com/cooperativesolutionism/nmsci/enumeration/MsgTypeEnum.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/converter/FlowNodeRegisterMsgConverter.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/converter/CentralPubkeyEmpowerMsgConverter.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/converter/CentralPubkeyLockedMsgConverter.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/converter/FlowNodeLockedMsgConverter.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/converter/TransactionRecordMsgConverter.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/converter/TransactionMountMsgConverter.java`
  - 6 write controllers listed in Task 2.
- Modify protocol field length consumers:
  - `src/main/java/com/cooperativesolutionism/nmsci/model/*.java` message models with `@ByteArraySize(33/64)`
  - `src/main/java/com/cooperativesolutionism/nmsci/service/*MsgService.java` public-key length checks
  - `src/main/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainSupport.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/config/properties/NmsciProperties.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java`
- Modify `docs/code-quality-audit-status.md`: record repair and verification.

---

## Task 1: Add Constants Class With Contract Tests

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengthsContractTest.java`
- Create: `src/main/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengths.java`

- [ ] **Step 1: Write the failing constants contract test**

Create `src/test/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengthsContractTest.java` with this content:

```java
package com.cooperativesolutionism.nmsci.constant;

import com.cooperativesolutionism.nmsci.annotation.ByteArraySize;
import com.cooperativesolutionism.nmsci.controller.CentralPubkeyEmpowerMsgController;
import com.cooperativesolutionism.nmsci.controller.CentralPubkeyLockedMsgController;
import com.cooperativesolutionism.nmsci.controller.FlowNodeLockedMsgController;
import com.cooperativesolutionism.nmsci.controller.FlowNodeRegisterMsgController;
import com.cooperativesolutionism.nmsci.controller.TransactionMountMsgController;
import com.cooperativesolutionism.nmsci.controller.TransactionRecordMsgController;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.converter.MessageConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolByteLengthsContractTest {

    @Test
    void messageTypeSizesUseProtocolByteLengths() {
        assertSize(MsgTypeEnum.FlowNodeRegisterMsg,
                ProtocolByteLengths.FLOW_NODE_REGISTER_STORED_BYTES,
                ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES);
        assertSize(MsgTypeEnum.CentralPubkeyEmpowerMsg,
                ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_STORED_BYTES,
                ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES);
        assertSize(MsgTypeEnum.CentralPubkeyLockedMsg,
                ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_STORED_BYTES,
                ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES);
        assertSize(MsgTypeEnum.FlowNodeLockedMsg,
                ProtocolByteLengths.FLOW_NODE_LOCKED_STORED_BYTES,
                ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES);
        assertSize(MsgTypeEnum.TransactionRecordMsg,
                ProtocolByteLengths.TRANSACTION_RECORD_STORED_BYTES,
                ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES);
        assertSize(MsgTypeEnum.TransactionMountMsg,
                ProtocolByteLengths.TRANSACTION_MOUNT_STORED_BYTES,
                ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES);
    }

    @Test
    void converterExpectedSizesMatchMessageTypeInboundSizes() {
        for (MessageConverter<?> converter : Map.of(
                MsgTypeEnum.FlowNodeRegisterMsg, new FlowNodeRegisterMsgConverter(),
                MsgTypeEnum.CentralPubkeyEmpowerMsg, new CentralPubkeyEmpowerMsgConverter(),
                MsgTypeEnum.CentralPubkeyLockedMsg, new CentralPubkeyLockedMsgConverter(),
                MsgTypeEnum.FlowNodeLockedMsg, new FlowNodeLockedMsgConverter(),
                MsgTypeEnum.TransactionRecordMsg, new TransactionRecordMsgConverter(),
                MsgTypeEnum.TransactionMountMsg, new TransactionMountMsgConverter()
        ).values()) {
            assertEquals(converter.msgType().getInboundSize(), converter.expectedSize(), converter.msgType().name());
        }
    }

    @Test
    void writeControllerByteArraySizeAnnotationsMatchInboundSizes() throws Exception {
        assertByteArraySize(FlowNodeRegisterMsgController.class, "saveFlowNodeRegisterMsg",
                ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES);
        assertByteArraySize(CentralPubkeyEmpowerMsgController.class, "saveCentralPubkeyEmpowerMsg",
                ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES);
        assertByteArraySize(CentralPubkeyLockedMsgController.class, "saveCentralPubkeyLockedMsg",
                ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES);
        assertByteArraySize(FlowNodeLockedMsgController.class, "saveFlowNodeLockedMsg",
                ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES);
        assertByteArraySize(TransactionRecordMsgController.class, "saveTransactionRecordMsg",
                ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES);
        assertByteArraySize(TransactionMountMsgController.class, "saveTransactionMountMsg",
                ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES);
    }

    private static void assertSize(MsgTypeEnum msgType, int storedBytes, int inboundBytes) {
        assertEquals(storedBytes, msgType.getSize(), msgType.name() + " stored bytes");
        assertEquals(inboundBytes, msgType.getInboundSize(), msgType.name() + " inbound bytes");
    }

    private static void assertByteArraySize(Class<?> controllerClass, String methodName, int expectedValue)
            throws Exception {
        Method method = controllerClass.getMethod(methodName, byte[].class);
        ByteArraySize byteArraySize = method.getParameters()[0].getAnnotation(ByteArraySize.class);

        assertEquals(expectedValue, byteArraySize.value(), controllerClass.getSimpleName() + "." + methodName);
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest test
```

Expected: FAIL during test compilation because `ProtocolByteLengths` does not exist yet.

- [ ] **Step 3: Add the constants class**

Create `src/main/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengths.java` with this content:

```java
package com.cooperativesolutionism.nmsci.constant;

public final class ProtocolByteLengths {

    private ProtocolByteLengths() {
    }

    public static final int UUID_BYTES = 16;
    public static final int RAW_PRIVATE_KEY_BYTES = 32;
    public static final int COMPRESSED_PUBLIC_KEY_BYTES = 33;
    public static final int RS_SIGNATURE_BYTES = 64;

    public static final int FLOW_NODE_REGISTER_INBOUND_BYTES = 123;
    public static final int CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES = 148;
    public static final int CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES = 115;
    public static final int FLOW_NODE_LOCKED_INBOUND_BYTES = 148;
    public static final int TRANSACTION_RECORD_INBOUND_BYTES = 263;
    public static final int TRANSACTION_MOUNT_INBOUND_BYTES = 269;

    public static final int FLOW_NODE_REGISTER_STORED_BYTES = 123;
    public static final int CENTRAL_PUBKEY_EMPOWER_STORED_BYTES = 220;
    public static final int CENTRAL_PUBKEY_LOCKED_STORED_BYTES = 187;
    public static final int FLOW_NODE_LOCKED_STORED_BYTES = 220;
    public static final int TRANSACTION_RECORD_STORED_BYTES = 335;
    public static final int TRANSACTION_MOUNT_STORED_BYTES = 341;
}
```

- [ ] **Step 4: Run the focused constants test and verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest test
```

Expected: PASS, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit the constants and test**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengths.java src/test/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengthsContractTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 提取协议长度常量"
```

---

## Task 2: Reuse Message Inbound And Stored Length Constants

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/enumeration/MsgTypeEnum.java`
- Modify: 6 converter files under `src/main/java/com/cooperativesolutionism/nmsci/converter`
- Modify: 6 write controller files under `src/main/java/com/cooperativesolutionism/nmsci/controller`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengthsContractTest.java`

- [ ] **Step 1: Update `MsgTypeEnum` constructor arguments**

In `src/main/java/com/cooperativesolutionism/nmsci/enumeration/MsgTypeEnum.java`, add these static imports after the package line:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_STORED_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_STORED_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_LOCKED_STORED_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_REGISTER_STORED_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_MOUNT_STORED_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_RECORD_STORED_BYTES;
```

Change the enum constants to:

```java
    FlowNodeRegisterMsg((short) 0x0000, FLOW_NODE_REGISTER_STORED_BYTES, FLOW_NODE_REGISTER_INBOUND_BYTES, "流转节点注册信息"),
    CentralPubkeyEmpowerMsg((short) 0x0001, CENTRAL_PUBKEY_EMPOWER_STORED_BYTES, CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES, "中心公钥公证信息"),
    CentralPubkeyLockedMsg((short) 0x0002, CENTRAL_PUBKEY_LOCKED_STORED_BYTES, CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES, "中心公钥冻结信息"),
    FlowNodeLockedMsg((short) 0x0003, FLOW_NODE_LOCKED_STORED_BYTES, FLOW_NODE_LOCKED_INBOUND_BYTES, "流转节点冻结信息"),
    TransactionRecordMsg((short) 0x0004, TRANSACTION_RECORD_STORED_BYTES, TRANSACTION_RECORD_INBOUND_BYTES, "交易记录信息"),
    TransactionMountMsg((short) 0x0005, TRANSACTION_MOUNT_STORED_BYTES, TRANSACTION_MOUNT_INBOUND_BYTES, "交易挂载信息");
```

- [ ] **Step 2: Update converter `expectedSize()` methods**

For each converter, add one static import and return the matching constant:

`FlowNodeRegisterMsgConverter.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES;
```

```java
    public int expectedSize() {
        return FLOW_NODE_REGISTER_INBOUND_BYTES;
    }
```

`CentralPubkeyEmpowerMsgConverter.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;
```

```java
    public int expectedSize() {
        return CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;
    }
```

`CentralPubkeyLockedMsgConverter.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;
```

```java
    public int expectedSize() {
        return CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;
    }
```

`FlowNodeLockedMsgConverter.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES;
```

```java
    public int expectedSize() {
        return FLOW_NODE_LOCKED_INBOUND_BYTES;
    }
```

`TransactionRecordMsgConverter.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES;
```

```java
    public int expectedSize() {
        return TRANSACTION_RECORD_INBOUND_BYTES;
    }
```

`TransactionMountMsgConverter.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES;
```

```java
    public int expectedSize() {
        return TRANSACTION_MOUNT_INBOUND_BYTES;
    }
```

- [ ] **Step 3: Update write controller `@ByteArraySize` annotations**

For each controller, add the matching static import and replace the annotation literal:

`FlowNodeRegisterMsgController.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_REGISTER_INBOUND_BYTES;
```

```java
    public ResponseResult<FlowNodeRegisterMsg> saveFlowNodeRegisterMsg(@RequestBody @ByteArraySize(FLOW_NODE_REGISTER_INBOUND_BYTES) byte[] byteData) {
```

`CentralPubkeyEmpowerMsgController.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES;
```

```java
    public ResponseResult<CentralPubkeyEmpowerMsg> saveCentralPubkeyEmpowerMsg(@RequestBody @ByteArraySize(CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES) byte[] byteData) {
```

`CentralPubkeyLockedMsgController.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES;
```

```java
    public ResponseResult<CentralPubkeyLockedMsg> saveCentralPubkeyLockedMsg(@RequestBody @ByteArraySize(CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES) byte[] byteData) {
```

`FlowNodeLockedMsgController.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.FLOW_NODE_LOCKED_INBOUND_BYTES;
```

```java
    public ResponseResult<FlowNodeLockedMsg> saveFlowNodeLockedMsg(@RequestBody @ByteArraySize(FLOW_NODE_LOCKED_INBOUND_BYTES) byte[] byteData) {
```

`TransactionRecordMsgController.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_RECORD_INBOUND_BYTES;
```

```java
    public ResponseResult<TransactionRecordMsg> saveTransactionRecordMsg(@RequestBody @ByteArraySize(TRANSACTION_RECORD_INBOUND_BYTES) byte[] byteData) {
```

`TransactionMountMsgController.java`:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.TRANSACTION_MOUNT_INBOUND_BYTES;
```

```java
    public ResponseResult<TransactionMountMsg> saveTransactionMountMsg(@RequestBody @ByteArraySize(TRANSACTION_MOUNT_INBOUND_BYTES) byte[] byteData) {
```

- [ ] **Step 4: Run focused tests and hardcoded inbound scans**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest test
```

Expected: PASS, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

Run:

```powershell
rg -n '@ByteArraySize\((123|148|115|263|269)\)' src\main\java\com\cooperativesolutionism\nmsci\controller
```

Expected: no output.

Run:

```powershell
rg -n 'return (123|148|115|263|269);' src\main\java\com\cooperativesolutionism\nmsci\converter
```

Expected: no output.

- [ ] **Step 5: Commit message-length constant usage**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/enumeration/MsgTypeEnum.java src/main/java/com/cooperativesolutionism/nmsci/converter/FlowNodeRegisterMsgConverter.java src/main/java/com/cooperativesolutionism/nmsci/converter/CentralPubkeyEmpowerMsgConverter.java src/main/java/com/cooperativesolutionism/nmsci/converter/CentralPubkeyLockedMsgConverter.java src/main/java/com/cooperativesolutionism/nmsci/converter/FlowNodeLockedMsgConverter.java src/main/java/com/cooperativesolutionism/nmsci/converter/TransactionRecordMsgConverter.java src/main/java/com/cooperativesolutionism/nmsci/converter/TransactionMountMsgConverter.java src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeRegisterMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyEmpowerMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyLockedMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeLockedMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionRecordMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionMountMsgController.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 复用消息长度常量"
```

---

## Task 3: Reuse Protocol Field Length Constants

**Files:**
- Modify: message model classes with `@ByteArraySize(33/64)`
- Modify: service/config/protocol/util field length checks listed below
- Test: `src/test/java/com/cooperativesolutionism/nmsci/constant/ProtocolByteLengthsContractTest.java`

- [ ] **Step 1: Update message model `@ByteArraySize` annotations**

In these model files, add static imports and replace annotation literals:

- `CentralPubkeyEmpowerMsg.java`
- `CentralPubkeyLockedMsg.java`
- `FlowNodeLockedMsg.java`
- `FlowNodeRegisterMsg.java`
- `TransactionRecordMsg.java`
- `TransactionMountMsg.java`

Use:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;
```

Replace each public-key annotation:

```java
    @ByteArraySize(COMPRESSED_PUBLIC_KEY_BYTES)
```

Replace each RS-signature annotation:

```java
    @ByteArraySize(RS_SIGNATURE_BYTES)
```

All 6 listed message model files currently need both static imports because each has at least one compressed public-key field and at least one RS-signature field.

- [ ] **Step 2: Update service and consume public-key length checks**

Add this static import to each listed file:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
```

Replace `length != 33` with `length != COMPRESSED_PUBLIC_KEY_BYTES` in:

- `src/main/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainSupport.java`
- `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java`
- `src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyEmpowerMsgService.java`
- `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgService.java`
- `src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeLockedMsgService.java`
- `src/main/java/com/cooperativesolutionism/nmsci/service/TransactionRecordMsgService.java`
- `src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgService.java`

Do not change the existing exception messages.

- [ ] **Step 3: Update `NmsciProperties` key length checks**

In `src/main/java/com/cooperativesolutionism/nmsci/config/properties/NmsciProperties.java`, add:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RAW_PRIVATE_KEY_BYTES;
```

Replace:

```java
decoded.length == 33
decoded.length == 32
decodedPubkey.length != 33
decodedPrikey.length != 32
```

with:

```java
decoded.length == COMPRESSED_PUBLIC_KEY_BYTES
decoded.length == RAW_PRIVATE_KEY_BYTES
decodedPubkey.length != COMPRESSED_PUBLIC_KEY_BYTES
decodedPrikey.length != RAW_PRIVATE_KEY_BYTES
```

Keep the source-code hash regexp `{64}` unchanged because it is a hex-character count, not an RS-signature byte length.

- [ ] **Step 4: Update protocol and crypto utilities**

In `src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java`, remove local `UUID_BYTES` and `PUBKEY_BYTES` constants. Add:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.UUID_BYTES;
```

Change size expressions from `PUBKEY_BYTES` to `COMPRESSED_PUBLIC_KEY_BYTES`.

In `src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java`, add:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;
```

Replace:

```java
signature.length != 64
```

with:

```java
signature.length != RS_SIGNATURE_BYTES
```

Keep the existing exception message unchanged.

In `src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java`, add:

```java
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RAW_PRIVATE_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;
```

Add this constant near the existing curve constants:

```java
    private static final int RS_COMPONENT_BYTES = RS_SIGNATURE_BYTES / 2;
```

Replace RS signature and raw key size usages:

```java
byte[] rs = new byte[RS_SIGNATURE_BYTES];
byte[] rBytes = toFixed32Bytes(r);
byte[] sBytes = toFixed32Bytes(s);
System.arraycopy(rBytes, 0, rs, 0, RS_COMPONENT_BYTES);
System.arraycopy(sBytes, 0, rs, RS_COMPONENT_BYTES, RS_COMPONENT_BYTES);
```

Inside `toFixed32Bytes`, replace `32` and ASN.1 leading-zero `33` values with `RS_COMPONENT_BYTES` expressions:

```java
if (valueBytes.length == RS_COMPONENT_BYTES + 1 && valueBytes[0] == 0) {
    valueBytes = Arrays.copyOfRange(valueBytes, 1, RS_COMPONENT_BYTES + 1);
}
if (valueBytes.length > RS_COMPONENT_BYTES) {
    throw new IOException("Invalid DER signature scalar length");
}

byte[] fixed = new byte[RS_COMPONENT_BYTES];
System.arraycopy(valueBytes, 0, fixed, RS_COMPONENT_BYTES - valueBytes.length, valueBytes.length);
```

Replace compressed public-key and raw private-key length checks:

```java
compressedPubKey.length != COMPRESSED_PUBLIC_KEY_BYTES
rawPrivateKey.length < RAW_PRIVATE_KEY_BYTES
new byte[RAW_PRIVATE_KEY_BYTES]
RAW_PRIVATE_KEY_BYTES - rawPrivateKey.length
rawPrivateKey.length > RAW_PRIVATE_KEY_BYTES
Arrays.copyOfRange(rawPrivateKey, rawPrivateKey.length - RAW_PRIVATE_KEY_BYTES, rawPrivateKey.length)
rawPrivateKey.length != RAW_PRIVATE_KEY_BYTES
rsSignature.length != RS_SIGNATURE_BYTES
```

Replace RS split points:

```java
Arrays.copyOfRange(rsSignature, 0, RS_COMPONENT_BYTES)
Arrays.copyOfRange(rsSignature, RS_COMPONENT_BYTES, RS_SIGNATURE_BYTES)
Arrays.copyOfRange(rsSignature, RS_COMPONENT_BYTES, RS_SIGNATURE_BYTES)
```

Do not modify `MerkleTreeUtil`; its `32` and `64` values refer to SHA-256 hash bytes and two-hash concatenation, not this protocol constants task.

- [ ] **Step 5: Run focused tests and protocol-field scans**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest,Secp256k1EncryptUtilTest test
```

Expected: PASS. Expected count is 14 tests: 3 constants tests + 11 Secp256k1 tests.

Run:

```powershell
rg -n '@ByteArraySize\((33|64|123|148|115|263|269)\)' src\main\java\com\cooperativesolutionism\nmsci
```

Expected: no output.

Run:

```powershell
rg -n 'length != (33|64|32)|length == (33|64|32|16)|new byte\[(64|33|32|16)\]|private static final int UUID_BYTES|private static final int PUBKEY_BYTES' src\main\java\com\cooperativesolutionism\nmsci
```

Expected: remaining matches are allowed only when they are unrelated hash/Merkle lengths. Review any output before committing. Expected allowed matches include `MerkleTreeUtil.java` for 32-byte hashes and 64-byte two-hash buffers. No matches should remain in `service`, `model`, `controller`, `protocol/SignatureValidator.java`, `protocol/ProtocolRawBytesBuilder.java`, `config/properties/NmsciProperties.java`, or protocol key/signature checks in `Secp256k1EncryptUtil.java`.

- [ ] **Step 6: Commit protocol field length usage**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/model/CentralPubkeyEmpowerMsg.java src/main/java/com/cooperativesolutionism/nmsci/model/CentralPubkeyLockedMsg.java src/main/java/com/cooperativesolutionism/nmsci/model/FlowNodeLockedMsg.java src/main/java/com/cooperativesolutionism/nmsci/model/FlowNodeRegisterMsg.java src/main/java/com/cooperativesolutionism/nmsci/model/TransactionRecordMsg.java src/main/java/com/cooperativesolutionism/nmsci/model/TransactionMountMsg.java src/main/java/com/cooperativesolutionism/nmsci/consume/ConsumeChainSupport.java src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyLockedMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/CentralPubkeyEmpowerMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeRegisterMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/FlowNodeLockedMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/TransactionRecordMsgService.java src/main/java/com/cooperativesolutionism/nmsci/service/TransactionMountMsgService.java src/main/java/com/cooperativesolutionism/nmsci/config/properties/NmsciProperties.java src/main/java/com/cooperativesolutionism/nmsci/protocol/ProtocolRawBytesBuilder.java src/main/java/com/cooperativesolutionism/nmsci/protocol/SignatureValidator.java src/main/java/com/cooperativesolutionism/nmsci/util/Secp256k1EncryptUtil.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 复用协议字段长度常量"
```

---

## Task 4: Update Audit Status And Run Full Verification

**Files:**
- Modify: `docs/code-quality-audit-status.md`
- Test: focused constants test, targeted crypto/constants tests, full unit tests, full verify.

- [ ] **Step 1: Run focused and full verification**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest test
```

Expected: PASS, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest,Secp256k1EncryptUtilTest test
```

Expected: PASS, `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`.

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS. If no other tests change, expected Surefire total is 168 tests: existing 165 + 3 new `ProtocolByteLengthsContractTest` tests.

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS if Docker/Testcontainers is available. If no other tests change, expected totals are Surefire 168 and Failsafe 35.

- [ ] **Step 2: Update audit status**

In `docs/code-quality-audit-status.md`:

Add the spec/plan to the top `codex 修复范围` line:

```markdown
`docs/superpowers/specs/2026-06-16-protocol-byte-lengths-design.md` / `docs/superpowers/plans/2026-06-16-protocol-byte-lengths.md`（协议长度常量化）
```

Update the top `验证手段` line to include:

```markdown
`ProtocolByteLengthsContractTest`（3 tests）/ `ProtocolByteLengthsContractTest,Secp256k1EncryptUtilTest`（14 tests） + full `mvnw test`（168 tests） + full `mvnw verify`（surefire 168 + failsafe 35 tests）
```

Update the summary table unit test count from 165 to 168.

Under `### 2.4 本轮新增修复（2026-06-16）`, add:

```markdown
- ✅ **协议长度常量化**：新增 `ProtocolByteLengths` 统一消息入站/落库长度以及 UUID、压缩公钥、RS 签名、原始私钥等协议字段长度；写端点 `@ByteArraySize`、消息转换器、`MsgTypeEnum`、实体字段约束和生产校验复用同一组编译期常量，保留现有协议布局、异常类型和错误文案。
```

In `## 3. 有意延后`, remove or narrow these bullets:

```markdown
- 魔法值 `33`/`64` 散落 17+ 处 → 共享协议常量（Low）。
- 写控制器绕过 `ProtocolMessageCodec`，`@ByteArraySize` 长度第三处硬编码（Low）。
```

If implementation leaves only non-protocol hash/Merkle literals, remove the `33/64` protocol constants bullet entirely. Keep unrelated delayed bullets.

In `## 6. 验证记录与待办`, add:

```markdown
- ✅ focused surefire 通过：`.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest test`，3 tests passed（Failures 0 / Errors 0 / Skipped 0）。
- ✅ targeted surefire 通过：`.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest,Secp256k1EncryptUtilTest test`，14 tests passed（Failures 0 / Errors 0 / Skipped 0）。
```

Update full `mvnw test` and `mvnw verify` counts to 168 / Surefire 168 + Failsafe 35.

- [ ] **Step 3: Run stale audit wording scans**

Run:

```powershell
rg -n '@ByteArraySize.*第三处硬编码|写控制器绕过 `ProtocolMessageCodec`|33.*64.*共享协议常量|协议常量化' docs\code-quality-audit-status.md
```

Expected: it may show the new repair bullet containing `协议长度常量化`; it must not show the old delayed bullets about `@ByteArraySize` third hardcoding or `33/64` shared protocol constants.

- [ ] **Step 4: Commit audit update**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "docs: 更新协议长度常量化审计状态"
```

---

## Task 5: Final Review And Completion

**Files:**
- No planned code changes. Fix only issues found by final verification or review.

- [ ] **Step 1: Verify no hardcoded controller/model `@ByteArraySize` remains**

Run:

```powershell
rg -n '@ByteArraySize\((33|64|123|148|115|263|269)\)' src\main\java\com\cooperativesolutionism\nmsci
```

Expected: no output.

- [ ] **Step 2: Verify no hardcoded inbound converter return remains**

Run:

```powershell
rg -n 'return (123|148|115|263|269);' src\main\java\com\cooperativesolutionism\nmsci\converter
```

Expected: no output.

- [ ] **Step 3: Verify focused constants test still passes**

Run:

```powershell
.\mvnw.cmd -Dtest=ProtocolByteLengthsContractTest test
```

Expected: PASS, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 4: Check workspace hygiene and commit authors**

Run:

```powershell
git diff --check
git status --short --branch
git log --format="%h %an <%ae> %s" -10
```

Expected:

- `git diff --check` has no output.
- `git status --short --branch` shows a clean `dev` branch.
- New commits use `GPT5.5XH <gpt5.5xh@example.local>` and Chinese commit messages.

- [ ] **Step 5: Final report**

Report:

```text
已完成：协议长度常量化，新增 ProtocolByteLengths，替换写端点入站长度、转换器 expectedSize、MsgTypeEnum、实体 ByteArraySize 和生产协议字段长度校验中的重复数字；未改变协议布局、异常类型或错误文案。
验证：列出 ProtocolByteLengthsContractTest、targeted constants+crypto tests、mvnw test、mvnw verify 的结果和测试数量。
提交：列出本轮新增提交哈希和中文提交信息。
残余：哈希/Merkle 等非协议长度数字仍按设计保留；写 Service 模板化、ConsumeChainQueryService 去重等结构性重构继续留在审计清单。
```
