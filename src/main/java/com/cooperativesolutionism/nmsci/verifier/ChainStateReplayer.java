package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 有状态回放：按「入账顺序」重放全链消息，重建注册/授权/冻结/记录等状态，逐条校验与时序强相关的协议前提，
 * 从而让独立验证真正闭环（不再有「时序敏感项暂未覆盖」的星号）。
 *
 * <p><b>入账顺序的重建：</b>各中心签名消息的确认时间戳（微秒）即其入账时刻，故按
 * (confirmTimestamp 升序, 然后 18 字节 id = 2字节msgType‖16字节uuid 的无符号字典序) 排序即得入账顺序
 * （与 {@code msg_abstracts} 的 ORDER BY 一致）。流转节点注册无中心确认（confirmTimestamp=0）排在最前——
 * 它只被依赖、从不依赖他者，故排前不影响任何前提判定；依赖方（公证依赖注册、交易依赖公证、挂载依赖记录）
 * 的确认时间戳严格晚于被依赖方，且 msgType 编号恰按依赖方向递增，确保并列时仍正确定序。中心冻结即终止实例，
 * 其后不再有合法消息。
 *
 * <p><b>覆盖的时序敏感前提</b>（对照各 *MsgService 与 PROTOCOL）：消息发生时中心公钥未冻结；流转节点未冻结；
 * 交易/冻结的流转节点已对其中心公钥公证授权（{@code empowered[F]==C}）；公证时流转节点尚未授权过
 * （{@code existsByFlowNodePubkey} —— 每节点至多公证一次）；以及 id/注册/冻结唯一性与挂载引用完整性。
 *
 * <p><b>不在此处</b>：难度目标=入账时最新区块难度、消息中心公钥=区块头中心公钥、中心公钥轮换合法性——
 * 这三项需要区块上下文，在 {@link ChainVerifier} 的逐块线性遍历中判定。
 */
public final class ChainStateReplayer {

    private static final Comparator<ParsedMessage> INGESTION_ORDER =
            Comparator.comparingLong(ParsedMessage::confirmTimestamp)
                    .thenComparing(ChainStateReplayer::idBytes, Arrays::compareUnsigned);

    public List<CheckResult> replay(List<ParsedBlock> blocks) {
        List<ParsedMessage> messages = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            messages.addAll(block.messages());
        }
        messages.sort(INGESTION_ORDER);

        Set<String> seenIds = new HashSet<>();
        Set<String> registered = new HashSet<>();
        Map<String, String> empoweredCentralByNode = new HashMap<>();
        Set<String> flowLocked = new HashSet<>();
        Set<String> frozenCentrals = new HashSet<>();
        Map<UUID, String> consumeByRecordId = new HashMap<>();

        Check idUnique = new Check("消息 id 全链唯一");
        Check registerOnce = new Check("流转节点注册唯一");
        Check freezeOnce = new Check("中心公钥冻结唯一");
        Check empowerNotAlready = new Check("公证：流转节点未重复授权");
        Check nodeRegistered = new Check("引用的流转节点已注册");
        Check nodeAuthorized = new Check("交易/冻结：流转节点已授权该中心公钥");
        Check nodeNotLocked = new Check("交易/冻结：流转节点未冻结");
        Check centralNotFrozen = new Check("消息发生时中心公钥未冻结");
        Check mountRecordExists = new Check("挂载引用的交易记录存在");
        Check mountConsumeMatch = new Check("挂载消费节点公钥与被挂载记录一致");

        // 防御性诊断：合法中心签名消息的 confirmTimestamp 必为正（DateUtil.getCurrentMicros()）。出现 ≤0
        // 表示数据损坏/回填，入账顺序重建可能失真——仅作信息提示，不据此判失败（避免对合法链误报）。
        int suspiciousTimestamps = 0;

        for (ParsedMessage message : messages) {
            String idKey = message.type().getValue() + ":" + message.id();
            idUnique.applicable();
            if (!seenIds.add(idKey)) {
                idUnique.fail("重复消息 " + idKey);
            }

            if (message.centrallySigned() && message.confirmTimestamp() <= 0) {
                suspiciousTimestamps++;
            }

            String flow = hexOrNull(message.flowNodePubkey());
            String central = hexOrNull(message.centralPubkey());

            switch (message.type()) {
                case FlowNodeRegisterMsg -> {
                    registerOnce.applicable();
                    if (!registered.add(flow)) {
                        registerOnce.fail("流转节点重复注册 id=" + message.id());
                    }
                }
                case CentralPubkeyEmpowerMsg -> {
                    requireRegistered(nodeRegistered, registered, flow, message);
                    empowerNotAlready.applicable();
                    if (empoweredCentralByNode.containsKey(flow)) {
                        empowerNotAlready.fail("流转节点重复授权 id=" + message.id());
                    }
                    requireCentralNotFrozen(centralNotFrozen, frozenCentrals, central, message);
                    empoweredCentralByNode.put(flow, central);
                }
                case CentralPubkeyLockedMsg -> {
                    freezeOnce.applicable();
                    if (!frozenCentrals.add(central)) {
                        freezeOnce.fail("中心公钥重复冻结 id=" + message.id());
                    }
                }
                case FlowNodeLockedMsg -> {
                    requireNodeOperable(message, flow, central,
                            registered, empoweredCentralByNode, flowLocked, frozenCentrals,
                            nodeRegistered, nodeAuthorized, nodeNotLocked, centralNotFrozen);
                    flowLocked.add(flow);
                }
                case TransactionRecordMsg -> {
                    requireNodeOperable(message, flow, central,
                            registered, empoweredCentralByNode, flowLocked, frozenCentrals,
                            nodeRegistered, nodeAuthorized, nodeNotLocked, centralNotFrozen);
                    consumeByRecordId.put(message.id(), hexOrNull(message.consumeNodePubkey()));
                }
                case TransactionMountMsg -> {
                    requireNodeOperable(message, flow, central,
                            registered, empoweredCentralByNode, flowLocked, frozenCentrals,
                            nodeRegistered, nodeAuthorized, nodeNotLocked, centralNotFrozen);
                    mountRecordExists.applicable();
                    UUID recordId = message.mountedTransactionRecordId();
                    String recordConsume = consumeByRecordId.get(recordId);
                    if (recordConsume == null) {
                        mountRecordExists.fail("挂载引用不存在/尚未入账的交易记录 " + recordId + " id=" + message.id());
                    } else {
                        mountConsumeMatch.applicable();
                        if (!recordConsume.equals(hexOrNull(message.consumeNodePubkey()))) {
                            mountConsumeMatch.fail("挂载消费节点公钥与被挂载记录不一致 id=" + message.id());
                        }
                    }
                }
            }
        }

        List<CheckResult> results = new ArrayList<>();
        for (Check check : List.of(idUnique, registerOnce, freezeOnce, empowerNotAlready,
                nodeRegistered, nodeAuthorized, nodeNotLocked, centralNotFrozen,
                mountRecordExists, mountConsumeMatch)) {
            results.add(check.toResult());
        }
        results.add(CheckResult.passed("入账顺序回放", CheckCategory.STATEFUL_REPLAY,
                messages.size() + " 条消息按 (confirmTimestamp, id) 入账顺序重放完成"));
        if (suspiciousTimestamps > 0) {
            results.add(CheckResult.skipped("确认时间戳良构（信息性）", CheckCategory.STATEFUL_REPLAY,
                    suspiciousTimestamps + " 条中心签名消息 confirmTimestamp≤0，疑似损坏/回填，入账顺序重建可能失真"));
        }
        return results;
    }

    private static void requireNodeOperable(
            ParsedMessage message, String flow, String central,
            Set<String> registered, Map<String, String> empoweredCentralByNode,
            Set<String> flowLocked, Set<String> frozenCentrals,
            Check nodeRegistered, Check nodeAuthorized, Check nodeNotLocked, Check centralNotFrozen
    ) {
        requireRegistered(nodeRegistered, registered, flow, message);

        nodeAuthorized.applicable();
        if (central == null || !central.equals(empoweredCentralByNode.get(flow))) {
            nodeAuthorized.fail("流转节点未对该中心公钥公证授权 id=" + message.id());
        }

        nodeNotLocked.applicable();
        if (flowLocked.contains(flow)) {
            nodeNotLocked.fail("流转节点已冻结 id=" + message.id());
        }

        requireCentralNotFrozen(centralNotFrozen, frozenCentrals, central, message);
    }

    private static void requireRegistered(Check nodeRegistered, Set<String> registered, String flow, ParsedMessage message) {
        nodeRegistered.applicable();
        if (!registered.contains(flow)) {
            nodeRegistered.fail("引用未注册的流转节点 id=" + message.id());
        }
    }

    private static void requireCentralNotFrozen(Check centralNotFrozen, Set<String> frozenCentrals, String central, ParsedMessage message) {
        centralNotFrozen.applicable();
        if (frozenCentrals.contains(central)) {
            centralNotFrozen.fail("消息发生时其中心公钥已冻结 id=" + message.id());
        }
    }

    /** 18 字节 id：2字节 msgType（大端）‖ 16字节 uuid（大端），与 msg_abstracts 主键一致。 */
    private static byte[] idBytes(ParsedMessage message) {
        UUID id = message.id();
        return ByteBuffer.allocate(18)
                .putShort(message.type().getValue())
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();
    }

    private static String hexOrNull(byte[] bytes) {
        return bytes == null ? null : ByteArrayUtil.bytesToHex(bytes);
    }

    /** 单个回放前提的累计结果。 */
    private static final class Check {
        private final String name;
        private int total;
        private int failures;
        private String firstDetail;

        Check(String name) {
            this.name = name;
        }

        void applicable() {
            total++;
        }

        void fail(String detail) {
            failures++;
            if (firstDetail == null) {
                firstDetail = detail;
            }
        }

        CheckResult toResult() {
            if (total == 0) {
                return CheckResult.skipped(name, CheckCategory.STATEFUL_REPLAY, "无适用消息");
            }
            if (failures == 0) {
                return CheckResult.passed(name, CheckCategory.STATEFUL_REPLAY, total + " 条全部通过");
            }
            return CheckResult.failed(name, CheckCategory.STATEFUL_REPLAY,
                    failures + "/" + total + " 条失败，首例: " + firstDetail);
        }
    }
}
