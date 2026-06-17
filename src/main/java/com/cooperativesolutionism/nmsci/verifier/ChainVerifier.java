package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.enumeration.CurrencyTypeEnum;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.PoWUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.math.BigInteger;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 独立链验证器核心：对已解析的区块序列执行有序验证清单，复用写入侧同一批哈希/签名/PoW 工具，保证逐字节一致。
 *
 * <p>所有运算只依赖区块字节本身（及前一区块 id 做链衔接），不访问数据库或本服务运行态，
 * 因此可由任意第三方在离线环境独立运行（见 {@link VerifyChainCli}）。
 */
public final class ChainVerifier {

    /** 协议冻结的区块版本号（对应 {@code nmsci.block-version}）。 */
    public static final int EXPECTED_BLOCK_VERSION = 1;

    private static final byte[] ZERO_HASH = new byte[32];

    static {
        // 验签需 BouncyCastle "BC" provider（KeyFactory/曲线点解码）。在任意调用环境下幂等确保其已注册。
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public ChainVerificationResult verify(List<ParsedBlock> blocks, VerifierOptions options) {
        List<CheckResult> chainChecks = new ArrayList<>();

        if (blocks.isEmpty()) {
            chainChecks.add(CheckResult.skipped("区块文件解析", CheckCategory.STRUCTURAL, "未发现任何区块（.dat 为空或目录无区块文件）"));
            return new ChainVerificationResult(true, chainChecks, List.of());
        }

        chainChecks.add(CheckResult.passed("区块文件解析", CheckCategory.STRUCTURAL, "成功解析 " + blocks.size() + " 个区块"));

        List<BlockVerificationResult> blockResults = new ArrayList<>();
        ParsedBlock previousBlock = null;
        Set<String> frozenCentralsSeen = new HashSet<>();

        for (ParsedBlock block : blocks) {
            List<CheckResult> checks = verifyBlock(block, previousBlock, frozenCentralsSeen, options);
            blockResults.add(new BlockVerificationResult(
                    block.height(),
                    ByteArrayUtil.bytesToHex(block.blockId()),
                    block.datFileName(),
                    block.messages().size(),
                    checks));
            // 收集本区块的中心公钥冻结，供后续区块的轮换合法性判定（仅含已遍历区块，不含本块之后）
            for (ParsedMessage message : block.messages()) {
                if (message.type() == MsgTypeEnum.CentralPubkeyLockedMsg) {
                    frozenCentralsSeen.add(ByteArrayUtil.bytesToHex(message.centralPubkey()));
                }
            }
            previousBlock = block;
        }

        if (options.includeStatefulReplay()) {
            chainChecks.addAll(new ChainStateReplayer().replay(blocks));
        } else {
            chainChecks.add(CheckResult.skipped("有状态回放", CheckCategory.STATEFUL_REPLAY, "已按选项关闭"));
        }

        return new ChainVerificationResult(true, chainChecks, blockResults);
    }

    private List<CheckResult> verifyBlock(
            ParsedBlock block,
            ParsedBlock previousBlock,
            Set<String> frozenCentralsSeen,
            VerifierOptions options
    ) {
        List<CheckResult> checks = new ArrayList<>();

        // ---- 结构性 ----
        checks.add(block.version() == EXPECTED_BLOCK_VERSION
                ? CheckResult.passed("区块版本号", CheckCategory.STRUCTURAL, "版本 " + block.version())
                : CheckResult.failed("区块版本号", CheckCategory.STRUCTURAL,
                        "期望 " + EXPECTED_BLOCK_VERSION + "，实得 " + block.version()));

        checks.add(difficultyWellFormed("注册难度目标良构", block.registerDifficultyTarget()));
        checks.add(difficultyWellFormed("交易难度目标良构", block.transactionDifficultyTarget()));
        checks.add(centralPubkeyWellFormed(block));
        checks.add(messageTypeMatchesSection(block));

        // ---- 链衔接 ----
        checks.add(linkage(block, previousBlock, options));

        // ---- 密码学 ----
        checks.add(blockSignature(block));
        checks.add(merkleRoot(block));
        checks.add(maxMsgTimestamp(block));

        // ---- 中心公钥期望值 ----
        checks.add(centralPubkeyExpectation(block, options));

        // ---- 源码哈希 ----
        checks.add(sourceHash(block, options));

        // ---- 入账时点规则（需前驱区块上下文）----
        checks.add(difficultyMatchesIngestion(block, previousBlock));
        checks.add(centralRotationLegitimacy(block, previousBlock, frozenCentralsSeen));

        // ---- 单条消息无状态校验 ----
        List<ParsedMessage> messages = block.messages();
        checks.add(aggregate("消息金额为正", CheckCategory.STATELESS_MESSAGE,
                filter(messages, MsgTypeEnum.TransactionRecordMsg),
                m -> m.amount() > 0 ? null : "金额非正 " + m.amount() + " id=" + m.id()));
        checks.add(aggregate("消息币种合法", CheckCategory.STATELESS_MESSAGE,
                filter(messages, MsgTypeEnum.TransactionRecordMsg),
                m -> CurrencyTypeEnum.containsValue(m.currencyType()) ? null : "币种非法 " + m.currencyType() + " id=" + m.id()));
        checks.add(aggregate("成员签名均为低S", CheckCategory.STATELESS_MESSAGE, messages, this::checkLowS));
        checks.add(aggregate("工作量证明", CheckCategory.STATELESS_MESSAGE,
                messages.stream().filter(ParsedMessage::hasProofOfWork).toList(), this::checkProofOfWork));
        checks.add(aggregate("消息中心公钥与区块头一致", CheckCategory.STATELESS_MESSAGE,
                messages.stream().filter(ParsedMessage::centrallySigned).toList(),
                m -> Arrays.equals(m.centralPubkey(), block.centralPubkey()) ? null : "消息中心公钥≠区块头 id=" + m.id()));
        checks.add(aggregate("成员签名验证", CheckCategory.STATELESS_MESSAGE, messages, this::checkMemberSignatures));
        checks.add(aggregate("中心签名验证", CheckCategory.STATELESS_MESSAGE,
                messages.stream().filter(ParsedMessage::centrallySigned).toList(),
                m -> checkCentralSignature(block, m)));

        return checks;
    }

    private CheckResult linkage(ParsedBlock block, ParsedBlock previousBlock, VerifierOptions options) {
        byte[] prevDigest = block.previousBlockHeaderDigest();
        if (previousBlock == null) {
            if (options.startingPreviousHash() != null) {
                return Arrays.equals(prevDigest, options.startingPreviousHash())
                        ? CheckResult.passed("起始区块衔接锚点", CheckCategory.CRYPTO, "前区块头摘要等于给定锚点")
                        : CheckResult.failed("起始区块衔接锚点", CheckCategory.CRYPTO,
                                "前区块头摘要 " + ByteArrayUtil.bytesToHex(prevDigest) + " ≠ 锚点");
            }
            if (block.height() == 0L && Arrays.equals(prevDigest, ZERO_HASH)) {
                return CheckResult.passed("创世区块衔接", CheckCategory.CRYPTO, "高度0且前区块头摘要全0");
            }
            return CheckResult.skipped("起始区块衔接", CheckCategory.CRYPTO,
                    "高度=" + block.height() + "，前区块头摘要=" + ByteArrayUtil.bytesToHex(prevDigest)
                            + "（非创世且未给锚点，无法独立判定衔接）");
        }

        boolean heightOk = block.height() == previousBlock.height() + 1;
        boolean linkOk = Arrays.equals(prevDigest, previousBlock.blockId());
        if (heightOk && linkOk) {
            return CheckResult.passed("链衔接", CheckCategory.CRYPTO, "高度连续且前区块头摘要匹配");
        }
        StringBuilder detail = new StringBuilder();
        if (!heightOk) {
            detail.append("高度不连续(期望 ").append(previousBlock.height() + 1).append(" 实得 ").append(block.height()).append(") ");
        }
        if (!linkOk) {
            detail.append("前区块头摘要与上一区块 id 不匹配");
        }
        return CheckResult.failed("链衔接", CheckCategory.CRYPTO, detail.toString().trim());
    }

    private CheckResult blockSignature(ParsedBlock block) {
        boolean valid = verifyEcdsa(block.headerSigningPreimage(), block.centralSignature(), block.centralPubkey());
        return valid
                ? CheckResult.passed("区块中心签名", CheckCategory.CRYPTO, "签名在区块头中心公钥下验证通过")
                : CheckResult.failed("区块中心签名", CheckCategory.CRYPTO, "签名验证失败");
    }

    private CheckResult merkleRoot(ParsedBlock block) {
        List<byte[]> leaves = block.messages().stream().map(ParsedMessage::txid).toList();
        byte[] recomputed = MerkleTreeUtil.calcMerkleRoot(leaves);
        return Arrays.equals(recomputed, block.merkleRoot())
                ? CheckResult.passed("默克尔根", CheckCategory.CRYPTO, "由 " + leaves.size() + " 个叶子重算一致")
                : CheckResult.failed("默克尔根", CheckCategory.CRYPTO,
                        "重算 " + ByteArrayUtil.bytesToHex(recomputed) + " ≠ 区块头 " + ByteArrayUtil.bytesToHex(block.merkleRoot()));
    }

    private CheckResult maxMsgTimestamp(ParsedBlock block) {
        long expected = block.messages().stream().mapToLong(ParsedMessage::confirmTimestamp).max().orElse(0L);
        return block.maxMsgTimestamp() == expected
                ? CheckResult.passed("最大消息时间戳", CheckCategory.CRYPTO, "区块头值 " + expected + " 等于消息确认时间戳最大值")
                : CheckResult.failed("最大消息时间戳", CheckCategory.CRYPTO,
                        "区块头 " + block.maxMsgTimestamp() + " ≠ 重算最大值 " + expected);
    }

    private CheckResult centralPubkeyExpectation(ParsedBlock block, VerifierOptions options) {
        if (options.expectedCentralPubkey() == null) {
            return CheckResult.skipped("中心公钥符合期望", CheckCategory.CRYPTO, "未提供期望中心公钥");
        }
        return Arrays.equals(block.centralPubkey(), options.expectedCentralPubkey())
                ? CheckResult.passed("中心公钥符合期望", CheckCategory.CRYPTO, "区块头中心公钥与期望一致")
                : CheckResult.failed("中心公钥符合期望", CheckCategory.CRYPTO,
                        "区块头中心公钥 " + ByteArrayUtil.bytesToHex(block.centralPubkey()) + " ≠ 期望");
    }

    private CheckResult sourceHash(ParsedBlock block, VerifierOptions options) {
        String actual = ByteArrayUtil.bytesToHex(block.sourceCodeZipHash());
        if (options.expectedSourceHashHex() == null) {
            return CheckResult.skipped("源码哈希绑定", CheckCategory.SOURCE_HASH, "区块头声明 " + actual + "（未提供期望值，未比对）");
        }
        return actual.equalsIgnoreCase(options.expectedSourceHashHex())
                ? CheckResult.passed("源码哈希绑定", CheckCategory.SOURCE_HASH, "区块头源码哈希与期望一致")
                : CheckResult.failed("源码哈希绑定", CheckCategory.SOURCE_HASH,
                        "区块头 " + actual + " ≠ 期望 " + options.expectedSourceHashHex().toLowerCase());
    }

    private CheckResult difficultyWellFormed(String name, int nbits) {
        try {
            PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(nbits));
            return CheckResult.passed(name, CheckCategory.STRUCTURAL, String.format("nBits=0x%08X", nbits));
        } catch (RuntimeException e) {
            return CheckResult.failed(name, CheckCategory.STRUCTURAL, String.format("nBits=0x%08X 非法: %s", nbits, e.getMessage()));
        }
    }

    private CheckResult centralPubkeyWellFormed(ParsedBlock block) {
        byte[] pubkey = block.centralPubkey();
        if (pubkey.length != 33 || (pubkey[0] != 0x02 && pubkey[0] != 0x03)) {
            return CheckResult.failed("中心公钥良构", CheckCategory.STRUCTURAL, "非 33 字节压缩公钥(前缀应为02/03)");
        }
        try {
            Secp256k1EncryptUtil.compressedToPublicKey(pubkey);
            return CheckResult.passed("中心公钥良构", CheckCategory.STRUCTURAL, "33字节压缩点在 secp256k1 上有效");
        } catch (Exception e) {
            return CheckResult.failed("中心公钥良构", CheckCategory.STRUCTURAL, "曲线点解码失败: " + e.getMessage());
        }
    }

    private CheckResult messageTypeMatchesSection(ParsedBlock block) {
        return aggregate("消息类型与分段一致", CheckCategory.STRUCTURAL, block.messages(),
                m -> m.rawMsgType() == m.type().getValue()
                        ? null
                        : String.format("内嵌类型0x%04X≠分段0x%04X id=%s", m.rawMsgType(), m.type().getValue(), m.id()));
    }

    private String checkLowS(ParsedMessage message) {
        for (ParsedMessage.MemberSignature member : message.memberSignatures()) {
            try {
                if (Secp256k1EncryptUtil.isNotLowS(member.signature())) {
                    return member.signer() + " 签名非低S id=" + message.id();
                }
            } catch (Exception e) {
                return member.signer() + " 签名格式非法 id=" + message.id();
            }
        }
        return null;
    }

    private String checkProofOfWork(ParsedMessage message) {
        BigInteger target;
        try {
            target = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(message.difficultyTarget()));
        } catch (RuntimeException e) {
            return "难度nBits非法 id=" + message.id() + ": " + e.getMessage();
        }
        BigInteger hash = new BigInteger(1, Sha256Util.doubleDigest(message.verifyData()));
        return hash.compareTo(target) <= 0 ? null : "PoW哈希超过目标 id=" + message.id();
    }

    /**
     * 难度=入账时最新区块难度：区块 B 中各 PoW 消息（注册/记录/挂载）入账时所验证的难度即区块 B-1 的难度，
     * 故其难度字段须等于前驱区块对应难度。起始区块无前驱时跳过（锚定子链的首块难度无法独立核验）。
     */
    private CheckResult difficultyMatchesIngestion(ParsedBlock block, ParsedBlock previousBlock) {
        List<ParsedMessage> powMessages = block.messages().stream().filter(ParsedMessage::hasProofOfWork).toList();
        if (powMessages.isEmpty()) {
            return CheckResult.skipped("消息难度=入账难度", CheckCategory.STATEFUL_REPLAY, "本区块无 PoW 消息");
        }
        if (previousBlock == null) {
            return CheckResult.skipped("消息难度=入账难度", CheckCategory.STATEFUL_REPLAY,
                    "起始区块无前驱，" + powMessages.size() + " 条 PoW 消息的入账难度无法独立核验");
        }
        return aggregate("消息难度=入账难度", CheckCategory.STATEFUL_REPLAY, powMessages, message -> {
            int expected = message.type() == MsgTypeEnum.FlowNodeRegisterMsg
                    ? previousBlock.registerDifficultyTarget()
                    : previousBlock.transactionDifficultyTarget();
            return message.difficultyTarget() == expected
                    ? null
                    : String.format("难度字段0x%08X≠前区块0x%08X id=%s", message.difficultyTarget(), expected, message.id());
        });
    }

    /**
     * 中心公钥轮换合法性：区块头中心公钥相对前驱发生变化，仅当旧中心公钥已在更早区块被冻结时才合法
     * （冻结即终止旧实例、由新中心公钥的新实例续链）。否则视为非法换钥。
     */
    private CheckResult centralRotationLegitimacy(ParsedBlock block, ParsedBlock previousBlock, Set<String> frozenCentralsSeen) {
        if (previousBlock == null) {
            return CheckResult.skipped("中心公钥轮换合法性", CheckCategory.STATEFUL_REPLAY, "起始区块无前驱");
        }
        if (Arrays.equals(block.centralPubkey(), previousBlock.centralPubkey())) {
            return CheckResult.passed("中心公钥轮换合法性", CheckCategory.STATEFUL_REPLAY, "与前区块中心公钥一致");
        }
        String oldCentral = ByteArrayUtil.bytesToHex(previousBlock.centralPubkey());
        return frozenCentralsSeen.contains(oldCentral)
                ? CheckResult.passed("中心公钥轮换合法性", CheckCategory.STATEFUL_REPLAY, "中心公钥轮换，且旧公钥已在更早区块被冻结")
                : CheckResult.failed("中心公钥轮换合法性", CheckCategory.STATEFUL_REPLAY, "中心公钥发生轮换但旧公钥未被冻结（疑似非法换钥）");
    }

    private String checkMemberSignatures(ParsedMessage message) {
        for (ParsedMessage.MemberSignature member : message.memberSignatures()) {
            if (!verifyEcdsa(message.verifyData(), member.signature(), member.pubkey())) {
                return member.signer() + " 成员签名验证失败 id=" + message.id();
            }
        }
        return null;
    }

    private String checkCentralSignature(ParsedBlock block, ParsedMessage message) {
        return verifyEcdsa(message.centralSignedData(), message.centralSignature(), block.centralPubkey())
                ? null
                : "中心签名验证失败 id=" + message.id();
    }

    private static CheckResult aggregate(String name, CheckCategory category, List<ParsedMessage> messages, Function<ParsedMessage, String> check) {
        int total = 0;
        int failures = 0;
        String firstDetail = null;
        for (ParsedMessage message : messages) {
            total++;
            String failure = check.apply(message);
            if (failure != null) {
                failures++;
                if (firstDetail == null) {
                    firstDetail = failure;
                }
            }
        }
        if (total == 0) {
            return CheckResult.skipped(name, category, "无适用消息");
        }
        if (failures == 0) {
            return CheckResult.passed(name, category, total + " 条消息全部通过");
        }
        return CheckResult.failed(name, category, failures + "/" + total + " 条失败，首例: " + firstDetail);
    }

    private static List<ParsedMessage> filter(List<ParsedMessage> messages, MsgTypeEnum type) {
        return messages.stream().filter(message -> message.type() == type).toList();
    }

    private static boolean verifyEcdsa(byte[] data, byte[] rsSignature, byte[] compressedPubkey) {
        try {
            return Secp256k1EncryptUtil.verifySignature(
                    data,
                    rsSignature,
                    Secp256k1EncryptUtil.compressedToPublicKey(compressedPubkey));
        } catch (Exception e) {
            return false;
        }
    }
}
