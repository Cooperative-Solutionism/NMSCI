# API Error Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make API failure responses put client-facing details in `message`, keep failure `data` empty, require octet-stream on binary write endpoints, and stop treating arbitrary `IllegalArgumentException` as HTTP 400.

**Architecture:** First change the response wrapper contract, then convert known API-boundary parse and binary-request failures to `BadRequestException`, then narrow the global exception handler. Keep low-level utility and service exception types mostly unchanged; controllers and request parsing utilities form the HTTP boundary.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5, MockMvc standalone tests, Maven Surefire, existing `ResponseResult`/`ResponseCode`/`GlobalExceptionHandler` conventions.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-17-api-error-contract-design.md`.

In scope:

- Failed response details move from `data` to `message`.
- Failed response `data` is `null`.
- `IllegalArgumentException` is removed from the global HTTP 400 handler.
- Request UUID/hex parsing helpers throw `BadRequestException` for malformed user input.
- Controller path-variable UUID and hex parsing use the same request helper path.
- The six protocol write endpoints declare `application/octet-stream`.
- The six protocol write endpoints convert converter/service `IllegalArgumentException` failures into `BadRequestException`.
- Focused tests and full unit tests are updated.
- `docs/code-quality-audit-status.md` is updated once at the end after verification.

Out of scope:

- Route changes.
- Query parameter name changes.
- Success response shape changes.
- POST 200 to 201 migration.
- Idempotency or retry semantics changes.
- DTO getter naming changes.
- Broad service exception taxonomy rewrite.
- Converting low-level utilities to depend on web exception types.
- Updating `docs/code-quality-audit-status.md` before the implementation and verification are complete.

## File Structure

- Modify `src/main/java/com/cooperativesolutionism/nmsci/response/ResponseResult.java`: put failure detail text in `message`; keep failure `data` null.
- Create `src/test/java/com/cooperativesolutionism/nmsci/response/ResponseResultTest.java`: focused response wrapper contract tests.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java`: add required UUID/hex parse helpers and wrap malformed request values as `BadRequestException`.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java`: expect `BadRequestException` for malformed request UUID/hex values.
- Modify controller path parsing in:
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeRegisterMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyEmpowerMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeLockedMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyLockedMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionRecordMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionMountMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeController.java`
- Create `src/main/java/com/cooperativesolutionism/nmsci/controller/ApiRequestBoundary.java`: package-local helper that converts controller-boundary `IllegalArgumentException` failures to `BadRequestException`.
- Create `src/test/java/com/cooperativesolutionism/nmsci/controller/BinaryWriteEndpointContractTest.java`: verifies octet-stream consumes and invalid binary bodies become `BadRequestException`.
- Modify `src/main/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandler.java`: narrow handler mapping and return new failure shape.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandlerTest.java`: assert new failure JSON contract and plain `IllegalArgumentException` sanitization.
- Modify `docs/code-quality-audit-status.md`: one final audit update after all implementation verification passes.

---

## Task 1: Response Wrapper Failure Contract

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/response/ResponseResultTest.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/response/ResponseResult.java`

- [ ] **Step 1: Write the failing response wrapper tests**

Create `src/test/java/com/cooperativesolutionism/nmsci/response/ResponseResultTest.java`:

```java
package com.cooperativesolutionism.nmsci.response;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseResultTest {

    @Test
    void failureWithDetailUsesDetailAsMessageAndKeepsDataNull() {
        ResponseResult<Void> result = ResponseResult.failure(ResponseCode.BAD_REQUEST, "分页大小必须大于0");

        assertEquals(400, result.getCode());
        assertEquals("分页大小必须大于0", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failureWithBlankDetailFallsBackToResponseCodeMessage() {
        ResponseResult<Void> result = ResponseResult.failure(ResponseCode.NOT_FOUND, " ");

        assertEquals(404, result.getCode());
        assertEquals("Not Found", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void genericFailureKeepsCodeMessageAndNullData() {
        ResponseResult<Object> result = ResponseResult.failure(ResponseCode.CONFLICT);

        assertEquals(409, result.getCode());
        assertEquals("Conflict", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void failureRejectsSuccessResponseCode() {
        assertThrows(IllegalArgumentException.class, () -> ResponseResult.failure(ResponseCode.SUCCESS));
        assertThrows(IllegalArgumentException.class, () -> ResponseResult.failure(ResponseCode.SUCCESS, "不能成功"));
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd "-Dtest=ResponseResultTest" test
```

Expected: FAIL because the current `ResponseResult.failure(ResponseCode, T)` puts `"分页大小必须大于0"` in `data` and leaves `message` as `"Bad Request"`.

- [ ] **Step 3: Update `ResponseResult`**

Replace `src/main/java/com/cooperativesolutionism/nmsci/response/ResponseResult.java` with:

```java
package com.cooperativesolutionism.nmsci.response;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ResponseResult<T> {

    private int code;
    private String message;
    private T data;

    public ResponseResult(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ResponseResult<T> success(T data) {
        return new ResponseResult<>(
                ResponseCode.SUCCESS.getCode(),
                ResponseCode.SUCCESS.getMessage(),
                data
        );
    }

    public static <T> ResponseResult<T> failure(ResponseCode responseCode) {
        Assert.isTrue(responseCode.getCode() != ResponseCode.SUCCESS.getCode(), "对于失败响应，响应码不能为SUCCESS");
        return new ResponseResult<>(
                responseCode.getCode(),
                responseCode.getMessage(),
                null
        );
    }

    public static ResponseResult<Void> failure(ResponseCode responseCode, String detailMessage) {
        Assert.isTrue(responseCode.getCode() != ResponseCode.SUCCESS.getCode(), "对于失败响应，响应码不能为SUCCESS");
        return new ResponseResult<>(
                responseCode.getCode(),
                StringUtils.hasText(detailMessage) ? detailMessage : responseCode.getMessage(),
                null
        );
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
```

- [ ] **Step 4: Run the focused response tests and verify they pass**

Run:

```powershell
.\mvnw.cmd "-Dtest=ResponseResultTest" test
```

Expected: PASS with 4 tests.

- [ ] **Step 5: Commit the response wrapper contract**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/response/ResponseResult.java src/test/java/com/cooperativesolutionism/nmsci/response/ResponseResultTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 收敛失败响应消息契约"
```

---

## Task 2: Request Parse Boundary For UUID And Hex Inputs

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java`
- Modify controller path parsing in the eight controller files listed in the file-structure section.

- [ ] **Step 1: Update request parser tests for web-boundary exceptions**

Replace `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java` with:

```java
package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestParamParserTest {

    @Test
    void detectsNonBlankValues() {
        assertFalse(RequestParamParser.notBlank(null));
        assertFalse(RequestParamParser.notBlank(""));
        assertFalse(RequestParamParser.notBlank("  "));
        assertTrue(RequestParamParser.notBlank("abc"));
    }

    @Test
    void treatsBlankUuidParameterAsMissing() {
        assertNull(RequestParamParser.uuidOrNull(null));
        assertNull(RequestParamParser.uuidOrNull(""));
        assertNull(RequestParamParser.uuidOrNull("  "));
    }

    @Test
    void parsesRequiredAndOptionalUuidParameters() {
        UUID expected = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals(expected, RequestParamParser.uuid(expected.toString()));
        assertEquals(expected, RequestParamParser.uuidOrNull(expected.toString()));
    }

    @Test
    void malformedUuidParametersBecomeBadRequest() {
        BadRequestException required = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.uuid("not-a-uuid")
        );
        assertEquals("UUID格式不正确", required.getMessage());

        BadRequestException optional = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.uuidOrNull("not-a-uuid")
        );
        assertEquals("UUID格式不正确", optional.getMessage());
    }

    @Test
    void parsesRequiredAndOptionalHexBytes() {
        assertNull(RequestParamParser.hexBytesOrNull(null));
        assertNull(RequestParamParser.hexBytesOrNull(""));
        assertNull(RequestParamParser.hexBytesOrNull("  "));
        assertArrayEquals(new byte[]{0x00, 0x0f, (byte) 0xab}, RequestParamParser.hexBytes("000fab"));
        assertArrayEquals(new byte[]{0x00, 0x0f, (byte) 0xab}, RequestParamParser.hexBytesOrNull("000fab"));
    }

    @Test
    void malformedHexParametersBecomeBadRequest() {
        BadRequestException required = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.hexBytes("0g")
        );
        assertEquals("十六进制字符串包含非法字符", required.getMessage());

        BadRequestException optional = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.hexBytesOrNull("0g")
        );
        assertEquals("十六进制字符串包含非法字符", optional.getMessage());
    }
}
```

- [ ] **Step 2: Run parser tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dtest=RequestParamParserTest" test
```

Expected: FAIL because `RequestParamParser.uuid(...)` and `RequestParamParser.hexBytes(...)` do not exist and malformed values still propagate plain `IllegalArgumentException`.

- [ ] **Step 3: Update `RequestParamParser`**

Replace `src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java` with:

```java
package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;

import java.util.UUID;

public final class RequestParamParser {

    private static final String INVALID_UUID_MESSAGE = "UUID格式不正确";

    private RequestParamParser() {
    }

    public static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    public static UUID uuidOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return uuid(value);
    }

    public static byte[] hexBytes(String value) {
        try {
            return ByteArrayUtil.hexToBytes(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static byte[] hexBytesOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return hexBytes(value);
    }
}
```

- [ ] **Step 4: Route controller path parsing through `RequestParamParser`**

Apply these controller changes.

In `FlowNodeRegisterMsgController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
```

and replace `getFlowNodeRegisterMsgById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<FlowNodeRegisterMsg> getFlowNodeRegisterMsgById(@PathVariable("id") String id) {
    FlowNodeRegisterMsg flowNodeRegisterMsgMsg = flowNodeRegisterMsgMsgService.getFlowNodeRegisterMsgById(uuid(id));
    return ResponseResult.success(flowNodeRegisterMsgMsg);
}
```

In `CentralPubkeyEmpowerMsgController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
```

and replace `getCentralPubkeyEmpowerMsgById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<CentralPubkeyEmpowerMsg> getCentralPubkeyEmpowerMsgById(@PathVariable("id") String id) {
    CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgService.getCentralPubkeyEmpowerMsgById(uuid(id));
    return ResponseResult.success(centralPubkeyEmpowerMsg);
}
```

In `FlowNodeLockedMsgController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
```

and replace `getFlowNodeLockedMsgById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<FlowNodeLockedMsg> getFlowNodeLockedMsgById(@PathVariable("id") String id) {
    FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgService.getFlowNodeLockedMsgById(uuid(id));
    return ResponseResult.success(flowNodeLockedMsg);
}
```

In `CentralPubkeyLockedMsgController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
```

and replace `getCentralPubkeyLockedMsgById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<CentralPubkeyLockedMsg> getCentralPubkeyLockedMsgById(@PathVariable String id) {
    return ResponseResult.success(centralPubkeyLockedMsgService.getCentralPubkeyLockedMsgById(uuid(id)));
}
```

In `TransactionRecordMsgController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
```

and replace `getTransactionRecordMsgById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<TransactionRecordMsg> getTransactionRecordMsgById(@PathVariable("id") String id) {
    TransactionRecordMsg transactionRecordMsg = transactionRecordMsgService.getTransactionRecordMsgById(uuid(id));
    return ResponseResult.success(transactionRecordMsg);
}
```

In `TransactionMountMsgController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;
```

and replace `getTransactionMountMsgById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<TransactionMountMsg> getTransactionMountMsgById(@PathVariable String id) {
    TransactionMountMsg transactionMountMsg = transactionMountMsgService.getTransactionMountMsgById(uuid(id));
    return ResponseResult.success(transactionMountMsg);
}
```

In `ConsumeChainController`, replace the `java.util.UUID` import with:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.notBlank;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuid;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;
```

and replace `getConsumeChainById(...)` with:

```java
@GetMapping("/{id}")
public ResponseResult<ConsumeChainResponseDTO> getConsumeChainById(@PathVariable String id) {
    ConsumeChainResponseDTO consumeChainResponseDTO = consumeChainQueryService.getConsumeChainById(uuid(id));
    return ResponseResult.success(consumeChainResponseDTO);
}
```

In `FlowNodeController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytes;
```

and replace `getFlowNodeState(...)` with:

```java
@GetMapping("/{flowNodePubkey}")
public ResponseResult<FlowNodeStateResponseDTO> getFlowNodeState(@PathVariable String flowNodePubkey) {
    return ResponseResult.success(flowNodeRegisterMsgService.getFlowNodeState(hexBytes(flowNodePubkey)));
}
```

- [ ] **Step 5: Run focused parser and controller contract tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=RequestParamParserTest,FlowNodeStateEndpointTest,ConsumeChainPaginationTest,TransactionRecordPaginationTest,TransactionMountPaginationTest" test
```

Expected: PASS. `RequestParamParserTest` should report 6 tests. The other focused tests should continue passing because method signatures and return shapes are unchanged.

- [ ] **Step 6: Scan for direct UUID/hex parsing in controllers**

Run:

```powershell
rg -n "UUID\.fromString|ByteArrayUtil\.hexToBytes" src\main\java\com\cooperativesolutionism\nmsci\controller
```

Expected: no output.

- [ ] **Step 7: Commit request parse boundary**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java src/main/java/com/cooperativesolutionism/nmsci/controller
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 收敛API请求参数解析错误"
```

---

## Task 3: Binary Write Endpoint Media Type And Bad Request Boundary

**Files:**
- Create: `src/main/java/com/cooperativesolutionism/nmsci/controller/ApiRequestBoundary.java`
- Create: `src/test/java/com/cooperativesolutionism/nmsci/controller/BinaryWriteEndpointContractTest.java`
- Modify six binary write controllers.

- [ ] **Step 1: Add failing binary endpoint contract tests**

Create `src/test/java/com/cooperativesolutionism/nmsci/controller/BinaryWriteEndpointContractTest.java`:

```java
package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.converter.CentralPubkeyEmpowerMsgConverter;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeLockedMsgConverter;
import com.cooperativesolutionism.nmsci.converter.FlowNodeRegisterMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionMountMsgConverter;
import com.cooperativesolutionism.nmsci.converter.TransactionRecordMsgConverter;
import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryWriteEndpointContractTest {

    @Test
    void binaryWriteEndpointsDeclareOctetStreamConsumes() throws NoSuchMethodException {
        assertConsumesOctetStream(FlowNodeRegisterMsgController.class, "saveFlowNodeRegisterMsg");
        assertConsumesOctetStream(CentralPubkeyEmpowerMsgController.class, "saveCentralPubkeyEmpowerMsg");
        assertConsumesOctetStream(FlowNodeLockedMsgController.class, "saveFlowNodeLockedMsg");
        assertConsumesOctetStream(CentralPubkeyLockedMsgController.class, "saveCentralPubkeyLockedMsg");
        assertConsumesOctetStream(TransactionRecordMsgController.class, "saveTransactionRecordMsg");
        assertConsumesOctetStream(TransactionMountMsgController.class, "saveTransactionMountMsg");
    }

    @Test
    void invalidBinaryBodiesBecomeBadRequestException() {
        FlowNodeRegisterMsgController registerController = new FlowNodeRegisterMsgController();
        ReflectionTestUtils.setField(registerController, "flowNodeRegisterMsgConverter", new FlowNodeRegisterMsgConverter());
        assertInvalidBody(() -> registerController.saveFlowNodeRegisterMsg(new byte[1]));

        CentralPubkeyEmpowerMsgController empowerController = new CentralPubkeyEmpowerMsgController();
        ReflectionTestUtils.setField(empowerController, "centralPubkeyEmpowerMsgConverter", new CentralPubkeyEmpowerMsgConverter());
        assertInvalidBody(() -> empowerController.saveCentralPubkeyEmpowerMsg(new byte[1]));

        FlowNodeLockedMsgController flowNodeLockedController = new FlowNodeLockedMsgController();
        ReflectionTestUtils.setField(flowNodeLockedController, "flowNodeLockedMsgConverter", new FlowNodeLockedMsgConverter());
        assertInvalidBody(() -> flowNodeLockedController.saveFlowNodeLockedMsg(new byte[1]));

        CentralPubkeyLockedMsgController centralLockedController = new CentralPubkeyLockedMsgController();
        ReflectionTestUtils.setField(centralLockedController, "centralPubkeyLockedMsgConverter", new CentralPubkeyLockedMsgConverter());
        assertInvalidBody(() -> centralLockedController.saveCentralPubkeyLockedMsg(new byte[1]));

        TransactionRecordMsgController recordController = new TransactionRecordMsgController();
        ReflectionTestUtils.setField(recordController, "transactionRecordMsgConverter", new TransactionRecordMsgConverter());
        assertInvalidBody(() -> recordController.saveTransactionRecordMsg(new byte[1]));

        TransactionMountMsgController mountController = new TransactionMountMsgController();
        ReflectionTestUtils.setField(mountController, "transactionMountMsgConverter", new TransactionMountMsgConverter());
        assertInvalidBody(() -> mountController.saveTransactionMountMsg(new byte[1]));
    }

    private void assertConsumesOctetStream(Class<?> controllerType, String methodName) throws NoSuchMethodException {
        Method method = controllerType.getMethod(methodName, byte[].class);
        PostMapping postMapping = method.getAnnotation(PostMapping.class);

        assertArrayEquals(new String[]{MediaType.APPLICATION_OCTET_STREAM_VALUE}, postMapping.consumes());
    }

    private void assertInvalidBody(ThrowingCall call) {
        BadRequestException exception = assertThrows(BadRequestException.class, call::run);

        assertTrue(exception.getMessage().startsWith("Invalid byte array size, expected "));
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
```

- [ ] **Step 2: Run binary endpoint tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dtest=BinaryWriteEndpointContractTest" test
```

Expected: FAIL because the write endpoints do not declare `consumes` and invalid binary bodies currently throw plain `IllegalArgumentException`.

- [ ] **Step 3: Add controller boundary helper**

Create `src/main/java/com/cooperativesolutionism/nmsci/controller/ApiRequestBoundary.java`:

```java
package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;

import java.util.function.Supplier;

final class ApiRequestBoundary {

    private ApiRequestBoundary() {
    }

    static <T> T badRequestOnIllegalArgument(Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
```

- [ ] **Step 4: Update six write endpoints**

In each of the six controllers, add:

```java
import org.springframework.http.MediaType;

import static com.cooperativesolutionism.nmsci.controller.ApiRequestBoundary.badRequestOnIllegalArgument;
```

Then replace the save methods exactly as follows.

`FlowNodeRegisterMsgController`:

```java
@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseResult<FlowNodeRegisterMsg> saveFlowNodeRegisterMsg(@RequestBody @ByteArraySize(FLOW_NODE_REGISTER_INBOUND_BYTES) byte[] byteData) {
    return badRequestOnIllegalArgument(() -> {
        FlowNodeRegisterMsg flowNodeRegisterMsgMsg = flowNodeRegisterMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeRegisterMsgMsgService.saveFlowNodeRegisterMsg(flowNodeRegisterMsgMsg));
    });
}
```

`CentralPubkeyEmpowerMsgController`:

```java
@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseResult<CentralPubkeyEmpowerMsg> saveCentralPubkeyEmpowerMsg(@RequestBody @ByteArraySize(CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES) byte[] byteData) {
    return badRequestOnIllegalArgument(() -> {
        CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg = centralPubkeyEmpowerMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(centralPubkeyEmpowerMsgService.saveCentralPubkeyEmpowerMsg(centralPubkeyEmpowerMsg));
    });
}
```

`FlowNodeLockedMsgController`:

```java
@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseResult<FlowNodeLockedMsg> saveFlowNodeLockedMsg(@RequestBody @ByteArraySize(FLOW_NODE_LOCKED_INBOUND_BYTES) byte[] byteData) {
    return badRequestOnIllegalArgument(() -> {
        FlowNodeLockedMsg flowNodeLockedMsg = flowNodeLockedMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(flowNodeLockedMsgService.saveFlowNodeLockedMsg(flowNodeLockedMsg));
    });
}
```

`CentralPubkeyLockedMsgController`:

```java
@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseResult<CentralPubkeyLockedMsg> saveCentralPubkeyLockedMsg(@RequestBody @ByteArraySize(CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES) byte[] byteData) {
    return badRequestOnIllegalArgument(() -> {
        CentralPubkeyLockedMsg centralPubkeyLockedMsg = centralPubkeyLockedMsgConverter.fromByteArray(byteData);
        centralPubkeyLockedMsgService.saveCentralPubkeyLockedMsg(centralPubkeyLockedMsg);
        return ResponseResult.success(centralPubkeyLockedMsg);
    });
}
```

`TransactionRecordMsgController`:

```java
@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseResult<TransactionRecordMsg> saveTransactionRecordMsg(@RequestBody @ByteArraySize(TRANSACTION_RECORD_INBOUND_BYTES) byte[] byteData) {
    return badRequestOnIllegalArgument(() -> {
        TransactionRecordMsg transactionRecordMsg = transactionRecordMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(transactionRecordMsgService.saveTransactionRecordMsg(transactionRecordMsg));
    });
}
```

`TransactionMountMsgController`:

```java
@PostMapping(consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
public ResponseResult<TransactionMountMsg> saveTransactionMountMsg(@RequestBody @ByteArraySize(TRANSACTION_MOUNT_INBOUND_BYTES) byte[] byteData) {
    return badRequestOnIllegalArgument(() -> {
        TransactionMountMsg transactionMountMsg = transactionMountMsgConverter.fromByteArray(byteData);
        return ResponseResult.success(transactionMountMsgService.saveTransactionMountMsg(transactionMountMsg));
    });
}
```

- [ ] **Step 5: Run binary endpoint tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=BinaryWriteEndpointContractTest" test
```

Expected: PASS with 2 tests.

- [ ] **Step 6: Commit binary endpoint contract**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/controller src/test/java/com/cooperativesolutionism/nmsci/controller/BinaryWriteEndpointContractTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 明确二进制写端点契约"
```

---

## Task 4: Narrow Global Exception Mapping

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandler.java`
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandlerTest.java`

- [ ] **Step 1: Replace the global exception handler tests**

Replace `src/test/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandlerTest.java` with:

```java
package com.cooperativesolutionism.nmsci.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsBadRequestExceptionToBadRequest() throws Exception {
        mockMvc.perform(get("/failure/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求参数非法"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void doesNotMapPlainIllegalArgumentToBadRequest() throws Exception {
        mockMvc.perform(get("/failure/illegal-argument"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.message").value(not(containsString("参数错误"))))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsNotFoundExceptionToNotFound() throws Exception {
        mockMvc.perform(get("/failure/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsConflictExceptionToConflict() throws Exception {
        mockMvc.perform(get("/failure/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("资源冲突"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsDataIntegrityViolationToConflictWithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/failure/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(containsString("唯一约束")))
                .andExpect(jsonPath("$.message").value(not(containsString("duplicate key value"))))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void mapsUnknownExceptionToInternalServerErrorWithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/failure/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("服务器内部错误"))
                .andExpect(jsonPath("$.message").value(not(containsString("secret-internal-detail"))))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @RestController
    public static class FailingController {

        @GetMapping("/failure/illegal-argument")
        void illegalArgument() {
            throw new IllegalArgumentException("参数错误");
        }

        @GetMapping("/failure/bad-request")
        void badRequest() {
            throw new BadRequestException("请求参数非法");
        }

        @GetMapping("/failure/not-found")
        void notFound() {
            throw new NotFoundException("资源不存在");
        }

        @GetMapping("/failure/conflict")
        void conflict() {
            throw new ConflictException("资源冲突");
        }

        @GetMapping("/failure/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/failure/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-detail");
        }
    }
}
```

- [ ] **Step 2: Run handler tests and verify they fail**

Run:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerTest" test
```

Expected: FAIL because `IllegalArgumentException` still maps to 400 and failure details are not yet consistently returned through `message` by the handler.

- [ ] **Step 3: Update `GlobalExceptionHandler`**

Replace `src/main/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandler.java` with:

```java
package com.cooperativesolutionism.nmsci.exception;

import com.cooperativesolutionism.nmsci.response.ResponseCode;
import com.cooperativesolutionism.nmsci.response.ResponseResult;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误";
    private static final String CONFLICT_MESSAGE = "数据冲突：违反唯一约束";

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ResponseResult<Void>> handleBadRequest(BadRequestException e) {
        logger.warn("Bad request: {}", e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ResponseResult<Void>> handleNotFoundException(NotFoundException e) {
        logger.warn("Not found: {}", e.getMessage());
        return failure(HttpStatus.NOT_FOUND, ResponseCode.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResponseResult<Void>> handleConflictException(ConflictException e) {
        logger.warn("Conflict: {}", e.getMessage());
        return failure(HttpStatus.CONFLICT, ResponseCode.CONFLICT, e.getMessage());
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ResponseResult<Void>> handleValidationExceptions(Exception e) {
        logger.warn("Validation failed: {}", e.getMessage());
        return failure(HttpStatus.BAD_REQUEST, ResponseCode.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseResult<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        logger.warn("Data integrity conflict: {}", e.getMessage());
        return failure(HttpStatus.CONFLICT, ResponseCode.CONFLICT, CONFLICT_MESSAGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handleAllExceptions(Exception e) {
        logger.error("An unexpected error occurred: {}", e.getMessage(), e);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, ResponseCode.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_MESSAGE);
    }

    private ResponseEntity<ResponseResult<Void>> failure(HttpStatus status, ResponseCode responseCode, String detail) {
        return ResponseEntity.status(status).body(ResponseResult.failure(responseCode, detail));
    }
}
```

- [ ] **Step 4: Run focused API contract tests**

Run:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerTest,ResponseResultTest,RequestParamParserTest,BinaryWriteEndpointContractTest" test
```

Expected: PASS. Expected focused count is 6 handler tests + 4 response tests + 6 parser tests + 2 binary endpoint tests = 18 tests.

- [ ] **Step 5: Commit global exception mapping**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandler.java src/test/java/com/cooperativesolutionism/nmsci/exception/GlobalExceptionHandlerTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 收窄全局异常响应契约"
```

---

## Task 5: Targeted Regression And Audit Update

**Files:**
- Modify: `docs/code-quality-audit-status.md`

- [ ] **Step 1: Run API-focused Surefire verification**

Run:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerTest,ResponseResultTest,RequestParamParserTest,BinaryWriteEndpointContractTest,FlowNodeStateEndpointTest,ConsumeChainPaginationTest,TransactionRecordPaginationTest,TransactionMountPaginationTest" test
```

Expected: PASS. Record the actual test count from Maven output.

- [ ] **Step 2: Run full unit verification**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS. Record the actual Surefire test count from Maven output.

- [ ] **Step 3: Optionally run full verify when Docker is available**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS when Docker/Testcontainers is available. Record actual Surefire and Failsafe counts. If Docker/Testcontainers fails for environment reasons, record the exact failure reason separately from Surefire.

- [ ] **Step 4: Update audit status once**

In `docs/code-quality-audit-status.md`, update the top `codex 修复范围` line to append:

```markdown
、`docs/superpowers/specs/2026-06-17-api-error-contract-design.md` / `docs/superpowers/plans/2026-06-17-api-error-contract.md`（API 错误契约打磨）
```

Update the top `验证手段` line to include the focused API contract command and expected count. If Maven reports a different count after implementation, record the Maven output count instead of this expected count:

```markdown
targeted surefire `GlobalExceptionHandlerTest,ResponseResultTest,RequestParamParserTest,BinaryWriteEndpointContractTest,FlowNodeStateEndpointTest,ConsumeChainPaginationTest,TransactionRecordPaginationTest,TransactionMountPaginationTest`（29 tests）
```

Under `### 2.4 本轮新增修复（2026-06-16 / 2026-06-17）`, add:

```markdown
- ✅ **API 错误契约打磨**：失败响应详情改由 `message` 承载且 `data` 为空；`IllegalArgumentException` 不再全局映射为 400，控制器/请求解析边界将已知 UUID、hex 与二进制协议输入错误转换为 `BadRequestException`；6 个二进制写端点显式声明 `application/octet-stream`。
```

In `## 3. 有意延后`, remove or narrow these completed delayed items:

```markdown
- `IllegalArgumentException` 全局映射 400（误标内部故障 + 泄露 JDK/解析器原文）。
- `ResponseResult.failure` 把错误详情放类型化 `data` 而非 `message`。
- 写端点未声明 `consumes`（文档要求 octet-stream 未强制）。
```

Keep unrelated delayed API items such as POST 200 vs 201, DTO getter naming, idempotency/retry semantics, and broader API polish.

In `## 6. 验证记录与待办`, add the focused API command and actual counts. Update full `mvnw test` and `mvnw verify` bullets with actual counts from Steps 2 and 3.

- [ ] **Step 5: Run audit scans**

Run:

```powershell
rg -n "API 错误契约|IllegalArgumentException.*全局映射|ResponseResult\.failure|写端点未声明|application/octet-stream|BinaryWriteEndpointContractTest" docs\code-quality-audit-status.md
```

Expected: the completed API contract repair appears under completed fixes and validation records. The old delayed bullets about global `IllegalArgumentException`, `ResponseResult.failure` detail-in-data, and missing `consumes` must not remain as unresolved delayed items.

- [ ] **Step 6: Commit audit update**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "docs: 更新API错误契约审计状态"
```

---

## Task 6: Final Review And Completion

**Files:**
- No planned file edits. Fix only issues found by verification or review.

- [ ] **Step 1: Run final source scans**

Run:

```powershell
rg -n "UUID\.fromString|ByteArrayUtil\.hexToBytes" src\main\java\com\cooperativesolutionism\nmsci\controller
```

Expected: no output.

Run:

```powershell
rg -n "@PostMapping$|@PostMapping\([^)]*consumes" src\main\java\com\cooperativesolutionism\nmsci\controller
```

Expected: the six binary write endpoints have `consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE`. No bare `@PostMapping` remains in the six protocol message write controllers.

Run:

```powershell
rg -n "@ExceptionHandler\(\{IllegalArgumentException|@ExceptionHandler\(IllegalArgumentException" src\main\java\com\cooperativesolutionism\nmsci\exception
```

Expected: no output.

- [ ] **Step 2: Run final focused verification**

Run:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerTest,ResponseResultTest,RequestParamParserTest,BinaryWriteEndpointContractTest,FlowNodeStateEndpointTest,ConsumeChainPaginationTest,TransactionRecordPaginationTest,TransactionMountPaginationTest" test
```

Expected: PASS. Use the actual count from Task 5.

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
已完成：API 错误契约打磨。失败响应详情进入 message，失败 data 为空；全局异常处理不再把任意 IllegalArgumentException 映射为 400；请求解析和二进制写端点边界显式转换 BadRequestException；6 个写端点声明 application/octet-stream。
验证：列出 focused surefire、mvnw test、mvnw verify 的实际结果和测试数量。
提交：列出本轮新增提交哈希和中文提交信息。
残余：POST 201、幂等/重试语义、DTO getter 命名和更深服务异常分类仍按审计清单留待后续。
```
