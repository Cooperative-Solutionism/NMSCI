# Controller Parameter Parser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract duplicated optional request-parameter parsing helpers from controllers into one utility without changing API behavior.

**Architecture:** Add a final static utility in `com.cooperativesolutionism.nmsci.util` that preserves the current null/blank/parse semantics. Replace controller-private helpers in the target controllers with static calls to that utility, keeping path-variable parsing and required-parameter checks structurally unchanged. Update the audit status after focused and full verification.

**Tech Stack:** Java 21, Spring MVC controllers, JUnit 5, Maven Surefire.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-16-controller-param-parser-design.md`.

In scope:

- Add `RequestParamParser` with `notBlank`, `uuidOrNull`, and `hexBytesOrNull`.
- Add focused unit tests for the utility.
- Replace duplicated optional request-parameter parsing helpers in:
  - `FlowNodeRegisterMsgController`
  - `CentralPubkeyEmpowerMsgController`
  - `FlowNodeLockedMsgController`
  - `CentralPubkeyLockedMsgController`
  - `TransactionRecordMsgController`
  - `TransactionMountMsgController`
  - `ConsumeChainController`
  - `ReturningFlowRateController`
- Update `docs/code-quality-audit-status.md` after verification.

Out of scope:

- REST parameter name changes.
- Response structure changes.
- HTTP status-code contract changes.
- New exception types.
- Path-variable parsing cleanup when no duplicated optional helper is involved.
- Write-service template refactors.
- Protocol byte-length constant refactors.

## File Structure

- Create `src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java`: one focused utility for optional string request-parameter parsing.
- Create `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java`: focused unit coverage for null/blank, valid parsing, and invalid parsing propagation.
- Modify simple optional-hex controllers:
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeRegisterMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyEmpowerMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeLockedMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyLockedMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionRecordMsgController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionMountMsgController.java`
- Modify grouped-query controllers:
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java`
  - `src/main/java/com/cooperativesolutionism/nmsci/controller/ReturningFlowRateController.java`
- Modify `docs/code-quality-audit-status.md`: mark the controller parameter parser duplication fixed and update verification counts.

---

## Task 1: Add RequestParamParser Utility With Focused Tests

**Files:**
- Create: `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java`
- Create: `src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java`

- [ ] **Step 1: Write the failing parser test**

Create `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java` with this content:

```java
package com.cooperativesolutionism.nmsci.util;

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
    void parsesUuidAndPropagatesInvalidUuid() {
        UUID expected = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals(expected, RequestParamParser.uuidOrNull(expected.toString()));
        assertThrows(IllegalArgumentException.class, () -> RequestParamParser.uuidOrNull("not-a-uuid"));
    }

    @Test
    void parsesHexBytesAndPropagatesInvalidHex() {
        assertNull(RequestParamParser.hexBytesOrNull(null));
        assertNull(RequestParamParser.hexBytesOrNull(""));
        assertNull(RequestParamParser.hexBytesOrNull("  "));
        assertArrayEquals(new byte[]{0x00, 0x0f, (byte) 0xab}, RequestParamParser.hexBytesOrNull("000fab"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> RequestParamParser.hexBytesOrNull("0g")
        );
        assertEquals("十六进制字符串包含非法字符", exception.getMessage());
    }
}
```

- [ ] **Step 2: Run the focused parser test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest test
```

Expected: FAIL at test compilation because `RequestParamParser` does not exist yet.

- [ ] **Step 3: Add the parser utility**

Create `src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java` with this content:

```java
package com.cooperativesolutionism.nmsci.util;

import java.util.UUID;

public final class RequestParamParser {

    private RequestParamParser() {
    }

    public static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static UUID uuidOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return UUID.fromString(value);
    }

    public static byte[] hexBytesOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }
}
```

- [ ] **Step 4: Run the focused parser test and verify it passes**

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest test
```

Expected: PASS, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Commit the utility and test**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/util/RequestParamParser.java src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 提取请求参数解析工具"
```

---

## Task 2: Replace Simple Optional-Hex Controller Helpers

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeRegisterMsgController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyEmpowerMsgController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeLockedMsgController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyLockedMsgController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionRecordMsgController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionMountMsgController.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java`

- [ ] **Step 1: Update FlowNodeRegisterMsgController**

In `FlowNodeRegisterMsgController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add this static import with the other imports:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
```

Change the service call from:

```java
                hexToBytesOrNull(flowNodePubkey),
```

to:

```java
                hexBytesOrNull(flowNodePubkey),
```

Delete the private helper:

```java
    private static byte[] hexToBytesOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }
```

- [ ] **Step 2: Update CentralPubkeyEmpowerMsgController**

In `CentralPubkeyEmpowerMsgController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
```

Change:

```java
                hexToBytesOrNull(flowNodePubkey),
```

to:

```java
                hexBytesOrNull(flowNodePubkey),
```

Delete its private `hexToBytesOrNull` method.

- [ ] **Step 3: Update FlowNodeLockedMsgController**

In `FlowNodeLockedMsgController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
```

Change:

```java
        Optional<FlowNodeLockedMsg> flowNodeLockedMsg = flowNodeLockedMsgService.findFlowNodeLockedMsgByFlowNodePubkey(hexToBytesOrNull(flowNodePubkey));
```

to:

```java
        Optional<FlowNodeLockedMsg> flowNodeLockedMsg = flowNodeLockedMsgService.findFlowNodeLockedMsgByFlowNodePubkey(hexBytesOrNull(flowNodePubkey));
```

Delete its private `hexToBytesOrNull` method.

- [ ] **Step 4: Update CentralPubkeyLockedMsgController**

In `CentralPubkeyLockedMsgController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
```

Change:

```java
        Optional<CentralPubkeyLockedMsg> centralPubkeyLockedMsg = centralPubkeyLockedMsgService.findCentralPubkeyLockedMsgByCentralPubkey(hexToBytesOrNull(centralPubkey));
```

to:

```java
        Optional<CentralPubkeyLockedMsg> centralPubkeyLockedMsg = centralPubkeyLockedMsgService.findCentralPubkeyLockedMsgByCentralPubkey(hexBytesOrNull(centralPubkey));
```

Delete its private `hexToBytesOrNull` method.

- [ ] **Step 5: Update TransactionRecordMsgController**

In `TransactionRecordMsgController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
```

Change:

```java
                hexToBytesOrNull(consumeNodePubkey),
                hexToBytesOrNull(flowNodePubkey),
```

to:

```java
                hexBytesOrNull(consumeNodePubkey),
                hexBytesOrNull(flowNodePubkey),
```

Delete its private `hexToBytesOrNull` method.

- [ ] **Step 6: Update TransactionMountMsgController**

In `TransactionMountMsgController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;
```

Change:

```java
                hexToBytesOrNull(consumeNodePubkey),
                hexToBytesOrNull(flowNodePubkey),
                uuidOrNull(mountedTransactionRecordId),
```

to:

```java
                hexBytesOrNull(consumeNodePubkey),
                hexBytesOrNull(flowNodePubkey),
                uuidOrNull(mountedTransactionRecordId),
```

The `uuidOrNull` call text stays the same but now resolves to the static import. Delete both private helpers:

```java
    private static byte[] hexToBytesOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return UUID.fromString(value);
    }
```

Keep `import java.util.UUID;` because `getTransactionMountMsgById` still uses `UUID.fromString(id)`.

- [ ] **Step 7: Run focused compile/test checks**

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest test
```

Expected: PASS, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

Run:

```powershell
rg -n 'private static byte\[\] hexToBytesOrNull|private static UUID uuidOrNull' src\main\java\com\cooperativesolutionism\nmsci\controller\FlowNodeRegisterMsgController.java src\main\java\com\cooperativesolutionism\nmsci\controller\CentralPubkeyEmpowerMsgController.java src\main\java\com\cooperativesolutionism\nmsci\controller\FlowNodeLockedMsgController.java src\main\java\com\cooperativesolutionism\nmsci\controller\CentralPubkeyLockedMsgController.java src\main\java\com\cooperativesolutionism\nmsci\controller\TransactionRecordMsgController.java src\main\java\com\cooperativesolutionism\nmsci\controller\TransactionMountMsgController.java
```

Expected: no output. Exit code 1 is acceptable for no matches.

- [ ] **Step 8: Commit the simple controller replacements**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeRegisterMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyEmpowerMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/FlowNodeLockedMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/CentralPubkeyLockedMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionRecordMsgController.java src/main/java/com/cooperativesolutionism/nmsci/controller/TransactionMountMsgController.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 复用消息控制器参数解析"
```

---

## Task 3: Replace Grouped Query Controller Helpers

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java`
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/controller/ReturningFlowRateController.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/pagination/ConsumeChainPaginationTest.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/util/RequestParamParserTest.java`

- [ ] **Step 1: Update ConsumeChainController imports**

In `ConsumeChainController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.notBlank;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;
```

Keep `import java.util.UUID;` because `getConsumeChainById` still uses `UUID.fromString(id)`.

- [ ] **Step 2: Replace ConsumeChainController helper calls**

Change the mounted transaction branch from:

```java
            result = consumeChainQueryService.getConsumeChainByMountedTransaction(UUID.fromString(mountedTransactionId), pageable);
```

to:

```java
            result = consumeChainQueryService.getConsumeChainByMountedTransaction(uuidOrNull(mountedTransactionId), pageable);
```

Change the pubkey branch from:

```java
            result = consumeChainQueryService.getConsumeChainByPubkey(
                    pubkey(startPubkey), pubkey(endPubkey), pubkey(nodePubkey), isLoop, pageable);
```

to:

```java
            result = consumeChainQueryService.getConsumeChainByPubkey(
                    hexBytesOrNull(startPubkey), hexBytesOrNull(endPubkey), hexBytesOrNull(nodePubkey), isLoop, pageable);
```

Change the related id branch from:

```java
            result = consumeChainQueryService.getConsumeChainByRelatedId(
                    uuid(startId), uuid(endId), uuid(nodeId), isLoop, pageable);
```

to:

```java
            result = consumeChainQueryService.getConsumeChainByRelatedId(
                    uuidOrNull(startId), uuidOrNull(endId), uuidOrNull(nodeId), isLoop, pageable);
```

Change edge pubkey call from:

```java
            edges = consumeChainQueryService.getConsumeChainEdgesByPubkey(
                    pubkey(sourcePubkey), pubkey(targetPubkey), currencyType, startTime, endTime, pageable);
```

to:

```java
            edges = consumeChainQueryService.getConsumeChainEdgesByPubkey(
                    hexBytesOrNull(sourcePubkey), hexBytesOrNull(targetPubkey), currencyType, startTime, endTime, pageable);
```

Change edge id call from:

```java
            edges = consumeChainQueryService.getConsumeChainEdgesById(
                    uuid(sourceId), uuid(targetId), currencyType, startTime, endTime, pageable);
```

to:

```java
            edges = consumeChainQueryService.getConsumeChainEdgesById(
                    uuidOrNull(sourceId), uuidOrNull(targetId), currencyType, startTime, endTime, pageable);
```

Delete these private helpers:

```java
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private UUID uuid(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return UUID.fromString(id);
    }

    private byte[] pubkey(String pubkey) {
        if (pubkey == null || pubkey.isBlank()) {
            return null;
        }
        return ByteArrayUtil.hexToBytes(pubkey);
    }
```

- [ ] **Step 3: Update ReturningFlowRateController imports**

In `ReturningFlowRateController`, remove:

```java
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
```

Add:

```java
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.hexBytesOrNull;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.notBlank;
import static com.cooperativesolutionism.nmsci.util.RequestParamParser.uuidOrNull;
```

Remove `import java.util.UUID;` because no direct UUID reference remains after this task.

- [ ] **Step 4: Replace ReturningFlowRateController helper calls**

Change:

```java
            request.setTarget(ByteArrayUtil.hexToBytes(targetPubkey));
            if (notBlank(sourcePubkey)) {
                request.setSource(ByteArrayUtil.hexToBytes(sourcePubkey));
```

to:

```java
            request.setTarget(hexBytesOrNull(targetPubkey));
            if (notBlank(sourcePubkey)) {
                request.setSource(hexBytesOrNull(sourcePubkey));
```

Change:

```java
            request.setTargetId(UUID.fromString(targetId));
            if (notBlank(sourceId)) {
                request.setSourceId(UUID.fromString(sourceId));
```

to:

```java
            request.setTargetId(uuidOrNull(targetId));
            if (notBlank(sourceId)) {
                request.setSourceId(uuidOrNull(sourceId));
```

Delete the private helper:

```java
    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
```

- [ ] **Step 5: Run targeted tests and helper scans**

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest,ConsumeChainPaginationTest test
```

Expected: PASS. Expected count is `Tests run: 7` from 4 parser tests and 3 consume-chain pagination tests, all 0 failures/errors/skips.

Run:

```powershell
rg -n 'private static byte\[\] hexToBytesOrNull|private static boolean notBlank|private static UUID uuidOrNull|private UUID uuid\(|private byte\[\] pubkey\(' src\main\java\com\cooperativesolutionism\nmsci\controller
```

Expected: no output. Exit code 1 is acceptable for no matches.

- [ ] **Step 6: Commit grouped query controller replacements**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/controller/ConsumeChainController.java src/main/java/com/cooperativesolutionism/nmsci/controller/ReturningFlowRateController.java
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "refactor: 复用集合查询参数解析"
```

---

## Task 4: Update Audit Status and Run Full Verification

**Files:**
- Modify: `docs/code-quality-audit-status.md`
- Test: focused parser test, targeted controller contract tests, full unit tests, full verify.

- [ ] **Step 1: Run focused and full verification**

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest test
```

Expected: PASS, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest,ConsumeChainPaginationTest test
```

Expected: PASS, `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`.

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS. If no other tests change, expected Surefire total is 165 tests: existing 161 + 4 new `RequestParamParserTest` tests.

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS if Docker/Testcontainers is available. If no other tests change, expected totals are surefire 165 and failsafe 35.

- [ ] **Step 2: Update the audit document**

In `docs/code-quality-audit-status.md`, update the top `codex 修复范围` line to include:

```markdown
`docs/superpowers/specs/2026-06-16-controller-param-parser-design.md` / `docs/superpowers/plans/2026-06-16-controller-param-parser.md`（控制器参数解析去重）
```

Update the top `验证手段` line to include focused surefire `RequestParamParserTest` and the exact full test counts from Step 1.

Under `### 2.4 本轮新增修复（2026-06-16）`, add this bullet:

```markdown
- ✅ **控制器参数解析去重**：新增 `RequestParamParser` 统一 optional query 参数的 blank/null 判断、UUID 解析和十六进制字节解析，替换消息控制器、消费链查询和回流率查询中的重复私有 helper；保留现有非法 UUID/hex 异常路径、必填参数 `BadRequestException` 文案和 path variable 解析行为。
```

In `## 3. 有意延后`, remove this structural-refactor bullet:

```markdown
- `hexToBytesOrNull` ×6 控制器 + `notBlank/uuid/pubkey` ×2 重复 → 抽共享工具（Low）。
```

In `## 7. 下一轮建议优先级`, if the line still says:

```markdown
1. 其余结构性重构（写 Service 模板化、常量化、去重）按团队节奏推进。
```

change it to:

```markdown
1. 其余结构性重构（写 Service 模板化、协议常量化等）按团队节奏推进。
```

In `## 6. 验证记录与待办`, add focused parser verification and update full counts using the exact command output. If Step 1 matched the expected counts, use:

```markdown
- ✅ focused surefire 通过：`.\mvnw.cmd -Dtest=RequestParamParserTest test`，4 tests passed（Failures 0 / Errors 0 / Skipped 0）。
```

and update full unit/full verify lines to surefire 165 and failsafe 35.

- [ ] **Step 3: Check stale audit wording**

Run:

```powershell
rg -n 'hexToBytesOrNull.*控制器|notBlank/uuid/pubkey|控制器.*抽共享工具' docs\code-quality-audit-status.md
```

Expected: no output. Exit code 1 is acceptable for no matches.

- [ ] **Step 4: Commit audit update**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name="GPT5.5XH" -c user.email="gpt5.5xh@example.local" commit --author="GPT5.5XH <gpt5.5xh@example.local>" -m "docs: 更新控制器参数解析审计状态"
```

---

## Task 5: Final Review and Completion

**Files:**
- No planned code changes. Fix only issues found by final verification or review.

- [ ] **Step 1: Verify no target controller-private helper remains**

Run:

```powershell
rg -n 'private static byte\[\] hexToBytesOrNull|private static boolean notBlank|private static UUID uuidOrNull|private UUID uuid\(|private byte\[\] pubkey\(' src\main\java\com\cooperativesolutionism\nmsci\controller
```

Expected: no output.

- [ ] **Step 2: Verify parser test still passes**

Run:

```powershell
.\mvnw.cmd -Dtest=RequestParamParserTest test
```

Expected: PASS, `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 3: Check workspace hygiene and commit authors**

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
已完成：控制器 optional request 参数解析去重，新增 RequestParamParser，替换重复 hex/uuid/notBlank helper，不改变 API 行为或错误路径。
验证：列出 RequestParamParserTest、targeted tests、mvnw test、mvnw verify 的结果和测试数量。
提交：列出本轮新增提交哈希和中文提交信息。
残余：写 Service 模板化、协议常量化等结构性重构仍按审计清单后续推进。
```
