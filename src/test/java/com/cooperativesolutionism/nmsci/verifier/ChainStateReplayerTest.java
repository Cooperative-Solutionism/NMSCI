package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有状态回放（入账顺序）覆盖与时序强相关的协议前提。直接构造 {@link ParsedMessage}（仅字段，无需签名），
 * 模拟生产侧永远不会产出的「非法链」，验证回放能精确检出每一类违规，并在合法链上不误报。
 */
class ChainStateReplayerTest {

    @Test
    void validChainPassesAllStatefulChecks() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);
        UUID recordId = uuid(100);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                record(recordId, consume, flow, central, 20),
                mount(uuid(4), recordId, consume, flow, central, 30));

        assertNoFailures(results);
    }

    @Test
    void detectsTransactionUnderFrozenCentral() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                freeze(uuid(3), central, 20),
                record(uuid(4), consume, flow, central, 30));

        assertFailed(results, "消息发生时中心公钥未冻结");
    }

    @Test
    void allowsTransactionIngestedBeforeFreeze() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);

        // 交易 ts=10 早于冻结 ts=20 → 入账时中心未冻结，合法
        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 5),
                record(uuid(3), consume, flow, central, 10),
                freeze(uuid(4), central, 20));

        assertPassed(results, "消息发生时中心公钥未冻结");
    }

    @Test
    void detectsUnauthorizedTransaction() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                record(uuid(2), consume, flow, central, 10));

        assertFailed(results, "交易/冻结：流转节点已授权该中心公钥");
    }

    @Test
    void detectsTransactionByLockedNode() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                flowLock(uuid(3), flow, central, 20),
                record(uuid(4), consume, flow, central, 30));

        assertFailed(results, "交易/冻结：流转节点未冻结");
    }

    @Test
    void detectsDoubleEmpower() {
        byte[] flow = pk(1);
        byte[] central = pk(2);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                empower(uuid(3), flow, central, 20));

        assertFailed(results, "公证：流转节点未对同一中心公钥重复授权");
    }

    @Test
    void allowsReEmpowerToNewCentralAfterRotation() {
        byte[] flow = pk(1);
        byte[] central1 = pk(2);
        byte[] central2 = pk(5);

        // 中心公钥轮换：central1 被冻结后，流转节点可对新的 central2 重新公证授权（#1 Option A，对齐 PROTOCOL.md）。
        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central1, 10),
                freeze(uuid(3), central1, 20),
                empower(uuid(4), flow, central2, 30));

        assertPassed(results, "公证：流转节点未对同一中心公钥重复授权");
    }

    @Test
    void allowsTransactionUnderNewCentralAfterRotation() {
        byte[] flow = pk(1);
        byte[] central1 = pk(2);
        byte[] central2 = pk(5);
        byte[] consume = pk(3);
        UUID recordId = uuid(100);

        // 轮换恢复后的业务连续性：流转节点重新授权 central2 后，可在 central2 下继续记账与挂载，全链无失败。
        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central1, 10),
                freeze(uuid(3), central1, 20),
                empower(uuid(4), flow, central2, 30),
                record(recordId, consume, flow, central2, 40),
                mount(uuid(6), recordId, consume, flow, central2, 50));

        assertNoFailures(results);
    }

    @Test
    void detectsMountOfNonexistentRecord() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                mount(uuid(3), uuid(999), consume, flow, central, 20));

        assertFailed(results, "挂载引用的交易记录存在");
    }

    @Test
    void detectsMountConsumePubkeyMismatch() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consumeA = pk(3);
        byte[] consumeB = pk(4);
        UUID recordId = uuid(100);

        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                record(recordId, consumeA, flow, central, 20),
                mount(uuid(4), recordId, consumeB, flow, central, 30));

        assertFailed(results, "挂载消费节点公钥与被挂载记录一致");
    }

    @Test
    void detectsReferenceToUnregisteredNode() {
        byte[] flow = pk(1);
        byte[] central = pk(2);

        List<CheckResult> results = replay(empower(uuid(1), flow, central, 10));

        assertFailed(results, "引用的流转节点已注册");
    }

    @Test
    void detectsDuplicateMessageId() {
        List<CheckResult> results = replay(
                register(uuid(1), pk(1)),
                register(uuid(1), pk(5)));

        assertFailed(results, "消息 id 全链唯一");
    }

    @Test
    void reconstructsIngestionOrderRegardlessOfBlockLayout() {
        byte[] flow = pk(1);
        byte[] central = pk(2);
        byte[] consume = pk(3);
        UUID recordId = uuid(100);

        // 挂载列在前但确认时间戳更晚 → 回放按 ts 排序后 record(20) 先于 mount(30)，合法
        List<CheckResult> results = replay(
                mount(uuid(4), recordId, consume, flow, central, 30),
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 10),
                record(recordId, consume, flow, central, 20));

        assertNoFailures(results);
    }

    @Test
    void flagsZeroTimestampAsInfoWithoutFailing() {
        byte[] flow = pk(1);
        byte[] central = pk(2);

        // 中心签名消息携带 confirmTimestamp=0（异常）→ 信息提示，但不判失败
        List<CheckResult> results = replay(
                register(uuid(1), flow),
                empower(uuid(2), flow, central, 0));

        assertNoFailures(results);
        assertEquals(CheckResult.Status.SKIPPED, byName(results, "确认时间戳良构（信息性）").status());
    }

    // ---- 构造工具 ----

    private static List<CheckResult> replay(ParsedMessage... messages) {
        ParsedBlock block = new ParsedBlock(new byte[ParsedBlock.HEADER_SIZE], List.of(messages), null);
        return new ChainStateReplayer().replay(List.of(block));
    }

    private static ParsedMessage register(UUID id, byte[] flow) {
        byte[] bytes = base(MsgTypeEnum.FlowNodeRegisterMsg, id);
        putPubkey(bytes, 26, flow);
        return new ParsedMessage(MsgTypeEnum.FlowNodeRegisterMsg, bytes);
    }

    private static ParsedMessage empower(UUID id, byte[] flow, byte[] central, long timestamp) {
        byte[] bytes = base(MsgTypeEnum.CentralPubkeyEmpowerMsg, id);
        putPubkey(bytes, 18, flow);
        putPubkey(bytes, 51, central);
        putTimestamp(bytes, timestamp);
        return new ParsedMessage(MsgTypeEnum.CentralPubkeyEmpowerMsg, bytes);
    }

    private static ParsedMessage freeze(UUID id, byte[] central, long timestamp) {
        byte[] bytes = base(MsgTypeEnum.CentralPubkeyLockedMsg, id);
        putPubkey(bytes, 18, central);
        putTimestamp(bytes, timestamp);
        return new ParsedMessage(MsgTypeEnum.CentralPubkeyLockedMsg, bytes);
    }

    private static ParsedMessage flowLock(UUID id, byte[] flow, byte[] central, long timestamp) {
        byte[] bytes = base(MsgTypeEnum.FlowNodeLockedMsg, id);
        putPubkey(bytes, 18, flow);
        putPubkey(bytes, 51, central);
        putTimestamp(bytes, timestamp);
        return new ParsedMessage(MsgTypeEnum.FlowNodeLockedMsg, bytes);
    }

    private static ParsedMessage record(UUID id, byte[] consume, byte[] flow, byte[] central, long timestamp) {
        byte[] bytes = base(MsgTypeEnum.TransactionRecordMsg, id);
        putPubkey(bytes, 36, consume);
        putPubkey(bytes, 69, flow);
        putPubkey(bytes, 102, central);
        putTimestamp(bytes, timestamp);
        return new ParsedMessage(MsgTypeEnum.TransactionRecordMsg, bytes);
    }

    private static ParsedMessage mount(UUID id, UUID mountedRecordId, byte[] consume, byte[] flow, byte[] central, long timestamp) {
        byte[] bytes = base(MsgTypeEnum.TransactionMountMsg, id);
        ByteBuffer.wrap(bytes)
                .putLong(18, mountedRecordId.getMostSignificantBits())
                .putLong(26, mountedRecordId.getLeastSignificantBits());
        putPubkey(bytes, 42, consume);
        putPubkey(bytes, 75, flow);
        putPubkey(bytes, 108, central);
        putTimestamp(bytes, timestamp);
        return new ParsedMessage(MsgTypeEnum.TransactionMountMsg, bytes);
    }

    private static byte[] base(MsgTypeEnum type, UUID id) {
        byte[] bytes = new byte[type.getSize()];
        ByteBuffer.wrap(bytes)
                .putShort(type.getValue())
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits());
        return bytes;
    }

    private static void putPubkey(byte[] bytes, int offset, byte[] pubkey) {
        System.arraycopy(pubkey, 0, bytes, offset, pubkey.length);
    }

    /** 中心签名类型的确认时间戳位于末尾 64字节中心签名之前 8字节，即偏移 size-72。 */
    private static void putTimestamp(byte[] bytes, long timestamp) {
        ByteBuffer.wrap(bytes).putLong(bytes.length - 72, timestamp);
    }

    private static byte[] pk(int seed) {
        byte[] pubkey = new byte[33];
        pubkey[0] = 0x02;
        pubkey[1] = (byte) seed;
        return pubkey;
    }

    private static UUID uuid(int n) {
        return new UUID(n, n);
    }

    private static void assertNoFailures(List<CheckResult> results) {
        assertTrue(results.stream().noneMatch(CheckResult::isFailure),
                () -> "期望无失败，实际: " + results.stream().filter(CheckResult::isFailure)
                        .map(c -> c.name() + " — " + c.detail()).toList());
    }

    private static void assertFailed(List<CheckResult> results, String name) {
        assertEquals(CheckResult.Status.FAILED, byName(results, name).status(),
                () -> name + " 应判定失败，实际: " + byName(results, name).detail());
    }

    private static void assertPassed(List<CheckResult> results, String name) {
        assertEquals(CheckResult.Status.PASSED, byName(results, name).status(),
                () -> name + " 应判定通过，实际: " + byName(results, name).detail());
    }

    private static CheckResult byName(List<CheckResult> results, String name) {
        return results.stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("未找到验证项: " + name));
    }
}
