package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 有状态回放（Phase 2）：跨区块重建注册/授权/记录等集合后，校验「与顺序无关」的引用完整性与唯一性规则。
 *
 * <p><b>覆盖范围（均为协议明列、与消息时序无关的存在性/唯一性约束）：</b>
 * 全链消息 id 唯一；流转节点注册唯一；中心公钥/流转节点冻结各自唯一；
 * 公证/冻结/交易/挂载所引用的流转节点均已注册；交易/挂载的流转节点对其中心公钥均有公证（已授权）；
 * 挂载所引用的交易记录存在且消费节点公钥一致。
 *
 * <p><b>有意未覆盖（与消息时序强相关，按「全链存在即违规」判定会对合法的较早消息误报）：</b>
 * 「消息发生时中心公钥/流转节点尚未冻结」「公证时尚未授权」「难度目标等于入账时最新区块难度」。
 * 这些需要精确的入账时序（含冻结即终止、确认时间戳并列）方能正确判定，留待后续 Phase。
 */
public final class ChainStateReplayer {

    public List<CheckResult> replay(List<ParsedBlock> blocks) {
        List<ParsedMessage> messages = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            messages.addAll(block.messages());
        }

        List<CheckResult> results = new ArrayList<>();

        // 集合构建（一遍）：唯一性在构建过程中即时检测。
        Set<String> seenMsgKeys = new HashSet<>();
        List<String> duplicateMsgKeys = new ArrayList<>();

        Map<String, Integer> registerCountByFlowNode = new HashMap<>();
        Map<String, Integer> centralLockCountByCentral = new HashMap<>();
        Map<String, Integer> flowLockCountByFlowNode = new HashMap<>();
        Set<String> empowerPairs = new HashSet<>();
        Map<UUID, String> consumePubkeyByRecordId = new HashMap<>();

        for (ParsedMessage message : messages) {
            String msgKey = message.type().getValue() + ":" + message.id();
            if (!seenMsgKeys.add(msgKey)) {
                duplicateMsgKeys.add(msgKey);
            }

            switch (message.type()) {
                case FlowNodeRegisterMsg ->
                        registerCountByFlowNode.merge(hex(message.flowNodePubkey()), 1, Integer::sum);
                case CentralPubkeyEmpowerMsg ->
                        empowerPairs.add(hex(message.flowNodePubkey()) + "|" + hex(message.centralPubkey()));
                case CentralPubkeyLockedMsg ->
                        centralLockCountByCentral.merge(hex(message.centralPubkey()), 1, Integer::sum);
                case FlowNodeLockedMsg ->
                        flowLockCountByFlowNode.merge(hex(message.flowNodePubkey()), 1, Integer::sum);
                case TransactionRecordMsg ->
                        consumePubkeyByRecordId.put(message.id(), hex(message.consumeNodePubkey()));
                case TransactionMountMsg -> {
                    // 引用类规则在第二遍校验
                }
            }
        }

        Set<String> registeredFlowNodes = registerCountByFlowNode.keySet();

        // 1. 全链消息 id 唯一
        results.add(duplicateMsgKeys.isEmpty()
                ? CheckResult.passed("消息 id 全链唯一", CheckCategory.STATEFUL_REPLAY, messages.size() + " 条消息无重复 (类型+uuid)")
                : CheckResult.failed("消息 id 全链唯一", CheckCategory.STATEFUL_REPLAY,
                        duplicateMsgKeys.size() + " 个重复，首例: " + duplicateMsgKeys.get(0)));

        // 2. 流转节点注册唯一
        results.add(uniquenessResult("流转节点注册唯一", registerCountByFlowNode, "流转节点公钥"));

        // 3. 中心公钥冻结唯一
        results.add(uniquenessResult("中心公钥冻结唯一", centralLockCountByCentral, "中心公钥"));

        // 4. 流转节点冻结唯一
        results.add(uniquenessResult("流转节点冻结唯一", flowLockCountByFlowNode, "流转节点公钥"));

        // 5. 引用的流转节点均已注册（公证/流转冻结/交易记录/交易挂载）
        results.add(referencedFlowNodesRegistered(messages, registeredFlowNodes));

        // 6. 交易记录/交易挂载的流转节点已授权（存在对应公证）
        results.add(transactionsAuthorized(messages, empowerPairs));

        // 7. 挂载引用的交易记录存在且消费节点公钥一致
        results.add(mountsReferenceValidRecords(messages, consumePubkeyByRecordId));

        // 范围说明（信息性）
        results.add(CheckResult.skipped("时序敏感规则", CheckCategory.STATEFUL_REPLAY,
                "未覆盖：消息发生时未冻结/未授权、难度目标等于入账时最新区块难度（需精确入账时序，避免对合法较早消息误报）"));

        return results;
    }

    private static CheckResult uniquenessResult(String name, Map<String, Integer> countByKey, String keyLabel) {
        List<String> duplicates = countByKey.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> abbreviate(entry.getKey()) + "×" + entry.getValue())
                .toList();
        if (duplicates.isEmpty()) {
            return CheckResult.passed(name, CheckCategory.STATEFUL_REPLAY, countByKey.size() + " 个" + keyLabel + "均唯一");
        }
        return CheckResult.failed(name, CheckCategory.STATEFUL_REPLAY,
                duplicates.size() + " 个" + keyLabel + "重复，首例: " + duplicates.get(0));
    }

    private static CheckResult referencedFlowNodesRegistered(List<ParsedMessage> messages, Set<String> registeredFlowNodes) {
        int total = 0;
        int failures = 0;
        String firstDetail = null;
        for (ParsedMessage message : messages) {
            boolean references = switch (message.type()) {
                case CentralPubkeyEmpowerMsg, FlowNodeLockedMsg, TransactionRecordMsg, TransactionMountMsg -> true;
                default -> false;
            };
            if (!references) {
                continue;
            }
            total++;
            if (!registeredFlowNodes.contains(hex(message.flowNodePubkey()))) {
                failures++;
                if (firstDetail == null) {
                    firstDetail = message.type() + " id=" + message.id() + " 引用未注册流转节点";
                }
            }
        }
        return aggregate("引用流转节点均已注册", total, failures, firstDetail);
    }

    private static CheckResult transactionsAuthorized(List<ParsedMessage> messages, Set<String> empowerPairs) {
        int total = 0;
        int failures = 0;
        String firstDetail = null;
        for (ParsedMessage message : messages) {
            boolean isTransaction = message.type() == MsgTypeEnum.TransactionRecordMsg
                    || message.type() == MsgTypeEnum.TransactionMountMsg;
            if (!isTransaction) {
                continue;
            }
            total++;
            // 单中心公钥模型下，授权即存在 (flowNode, central) 的公证对。若未来引入中心公钥轮换，
            // 轮换后未对该流转节点重新公证的合法交易会被此处误判，届时应将本规则移入时序敏感（暂未覆盖）集合。
            String pair = hex(message.flowNodePubkey()) + "|" + hex(message.centralPubkey());
            if (!empowerPairs.contains(pair)) {
                failures++;
                if (firstDetail == null) {
                    firstDetail = message.type() + " id=" + message.id() + " 的流转节点未对其中心公钥公证授权";
                }
            }
        }
        return aggregate("交易流转节点均已授权", total, failures, firstDetail);
    }

    private static CheckResult mountsReferenceValidRecords(List<ParsedMessage> messages, Map<UUID, String> consumePubkeyByRecordId) {
        int total = 0;
        int failures = 0;
        String firstDetail = null;
        for (ParsedMessage message : messages) {
            if (message.type() != MsgTypeEnum.TransactionMountMsg) {
                continue;
            }
            total++;
            UUID mountedId = message.mountedTransactionRecordId();
            String recordConsume = consumePubkeyByRecordId.get(mountedId);
            if (recordConsume == null) {
                failures++;
                if (firstDetail == null) {
                    firstDetail = "挂载 id=" + message.id() + " 引用的交易记录 " + mountedId + " 不存在";
                }
            } else if (!recordConsume.equals(hex(message.consumeNodePubkey()))) {
                failures++;
                if (firstDetail == null) {
                    firstDetail = "挂载 id=" + message.id() + " 消费节点公钥与被挂载记录不一致";
                }
            }
        }
        return aggregate("挂载引用记录存在且消费公钥一致", total, failures, firstDetail);
    }

    private static CheckResult aggregate(String name, int total, int failures, String firstDetail) {
        if (total == 0) {
            return CheckResult.skipped(name, CheckCategory.STATEFUL_REPLAY, "无适用消息");
        }
        if (failures == 0) {
            return CheckResult.passed(name, CheckCategory.STATEFUL_REPLAY, total + " 条全部通过");
        }
        return CheckResult.failed(name, CheckCategory.STATEFUL_REPLAY, failures + "/" + total + " 条失败，首例: " + firstDetail);
    }

    private static String hex(byte[] bytes) {
        return bytes == null ? "<null>" : ByteArrayUtil.bytesToHex(bytes);
    }

    private static String abbreviate(String hex) {
        if (hex == null || hex.length() <= 16) {
            return hex;
        }
        return hex.substring(0, 8) + "…" + hex.substring(hex.length() - 8);
    }
}
