package com.cooperativesolutionism.nmsci.verifier;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RS_SIGNATURE_BYTES;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 从区块体中按存储字节布局解析出的单条消息（只读切片）。
 *
 * <p>所有字段偏移与写入侧 {@link com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder} /
 * 各 {@code *MsgConverter} 严格对应；存储字节 = 入站 verifyData || 各成员签名 ||（中心签名类型追加）8字节确认时间戳 || 64字节中心签名。
 * {@link MsgTypeEnum#FlowNodeRegisterMsg} 无中心签名、无确认时间戳，存储字节等于入站字节。
 */
public final class ParsedMessage {

    /** 单个成员（节点）签名及其对应应验证的压缩公钥。 */
    public record MemberSignature(String signer, byte[] signature, byte[] pubkey) {
    }

    private static final int MSG_TYPE_OFFSET = 0;
    private static final int ID_OFFSET = Short.BYTES;
    private static final int CONFIRM_TIMESTAMP_BYTES = Long.BYTES;

    private final MsgTypeEnum type;
    private final byte[] stored;

    // 性能审计 QW5/finding #14：memberSignatures 的记忆化缓存。校验期 checkLowS 与 checkMemberSignatures 各遍历一次，
    // 记忆化后两次复用同一只读列表，省去第二次重建（各签名/公钥的 Arrays.copyOfRange 拷贝）。ParsedMessage 由只读
    // stored 构造、校验单飞（single-flight）串行执行，缓存幂等且不跨线程共享。
    private List<MemberSignature> memberSignaturesCache;

    ParsedMessage(MsgTypeEnum type, byte[] stored) {
        this.type = type;
        this.stored = stored;
    }

    public MsgTypeEnum type() {
        return type;
    }

    /** 完整存储字节（含中心签名）的副本。 */
    public byte[] stored() {
        return stored.clone();
    }

    public int storedSize() {
        return stored.length;
    }

    /** 记录内嵌的 2 字节消息类型字段（应与所在区块体分段一致）。 */
    public short rawMsgType() {
        return buffer().getShort(MSG_TYPE_OFFSET);
    }

    public UUID id() {
        ByteBuffer buffer = buffer();
        return new UUID(buffer.getLong(ID_OFFSET), buffer.getLong(ID_OFFSET + Long.BYTES));
    }

    /** 该消息的 txid（默克尔叶子）= reverseBytes(dblSHA256(完整存储字节))，复用写入侧算法。 */
    public byte[] txid() {
        return MerkleTreeUtil.calcTxid(stored);
    }

    public boolean centrallySigned() {
        return type != MsgTypeEnum.FlowNodeRegisterMsg;
    }

    public boolean hasProofOfWork() {
        return type == MsgTypeEnum.FlowNodeRegisterMsg
                || type == MsgTypeEnum.TransactionRecordMsg
                || type == MsgTypeEnum.TransactionMountMsg;
    }

    /** 工作量证明 / 各方签名所覆盖的前若干字节（前 N 项数据）。 */
    public int verifyDataSize() {
        return switch (type) {
            case FlowNodeRegisterMsg -> 59;
            case CentralPubkeyEmpowerMsg -> 84;
            case CentralPubkeyLockedMsg -> 51;
            case FlowNodeLockedMsg -> 84;
            case TransactionRecordMsg -> 135;
            case TransactionMountMsg -> 141;
        };
    }

    public byte[] verifyData() {
        return Arrays.copyOfRange(stored, 0, verifyDataSize());
    }

    /** 中心签名覆盖的数据 = 记录去掉末尾 64 字节中心签名（= verifyData || 各成员签名 || 8字节确认时间戳）。 */
    public byte[] centralSignedData() {
        return Arrays.copyOfRange(stored, 0, stored.length - RS_SIGNATURE_BYTES);
    }

    public byte[] centralSignature() {
        return Arrays.copyOfRange(stored, stored.length - RS_SIGNATURE_BYTES, stored.length);
    }

    /** 中心确认时间戳；{@link MsgTypeEnum#FlowNodeRegisterMsg} 无此字段，按协议约定返回 0。 */
    public long confirmTimestamp() {
        if (!centrallySigned()) {
            return 0L;
        }
        int offset = stored.length - RS_SIGNATURE_BYTES - CONFIRM_TIMESTAMP_BYTES;
        return buffer().getLong(offset);
    }

    public List<MemberSignature> memberSignatures() {
        List<MemberSignature> cached = memberSignaturesCache;
        if (cached == null) {
            cached = List.copyOf(buildMemberSignatures());
            memberSignaturesCache = cached;
        }
        return cached;
    }

    private List<MemberSignature> buildMemberSignatures() {
        List<MemberSignature> signatures = new ArrayList<>();
        switch (type) {
            case FlowNodeRegisterMsg -> signatures.add(member("flowNode", 59, 26));
            case CentralPubkeyEmpowerMsg -> signatures.add(member("flowNode", 84, 18));
            case CentralPubkeyLockedMsg -> signatures.add(member("central", 51, 18));
            case FlowNodeLockedMsg -> signatures.add(member("flowNode", 84, 18));
            case TransactionRecordMsg -> {
                signatures.add(member("consumeNode", 135, 36));
                signatures.add(member("flowNode", 199, 69));
            }
            case TransactionMountMsg -> {
                signatures.add(member("consumeNode", 141, 42));
                signatures.add(member("flowNode", 205, 75));
            }
        }
        return signatures;
    }

    /** 工作量证明难度目标字段（nBits），仅 PoW 类型有此字段。 */
    public int difficultyTarget() {
        return switch (type) {
            case FlowNodeRegisterMsg -> buffer().getInt(18);
            case TransactionRecordMsg -> buffer().getInt(28);
            case TransactionMountMsg -> buffer().getInt(34);
            default -> throw new IllegalStateException("消息类型无难度目标字段: " + type);
        };
    }

    /** 交易金额（仅 {@link MsgTypeEnum#TransactionRecordMsg}）。 */
    public long amount() {
        requireType(MsgTypeEnum.TransactionRecordMsg);
        return buffer().getLong(18);
    }

    /** 币种（仅 {@link MsgTypeEnum#TransactionRecordMsg}）。 */
    public short currencyType() {
        requireType(MsgTypeEnum.TransactionRecordMsg);
        return buffer().getShort(26);
    }

    /** 被挂载的交易记录 id（仅 {@link MsgTypeEnum#TransactionMountMsg}）。 */
    public UUID mountedTransactionRecordId() {
        requireType(MsgTypeEnum.TransactionMountMsg);
        ByteBuffer buffer = buffer();
        return new UUID(buffer.getLong(18), buffer.getLong(18 + Long.BYTES));
    }

    /** 流转节点公钥；类型无此字段时返回 null。 */
    public byte[] flowNodePubkey() {
        Integer offset = switch (type) {
            case FlowNodeRegisterMsg -> 26;
            case CentralPubkeyEmpowerMsg, FlowNodeLockedMsg -> 18;
            case TransactionRecordMsg -> 69;
            case TransactionMountMsg -> 75;
            case CentralPubkeyLockedMsg -> null;
        };
        return offset == null ? null : pubkeyAt(offset);
    }

    /** 中心公钥；类型无此字段时返回 null。 */
    public byte[] centralPubkey() {
        Integer offset = switch (type) {
            case CentralPubkeyEmpowerMsg, FlowNodeLockedMsg -> 51;
            case CentralPubkeyLockedMsg -> 18;
            case TransactionRecordMsg -> 102;
            case TransactionMountMsg -> 108;
            case FlowNodeRegisterMsg -> null;
        };
        return offset == null ? null : pubkeyAt(offset);
    }

    /** 消费节点公钥；类型无此字段时返回 null。 */
    public byte[] consumeNodePubkey() {
        Integer offset = switch (type) {
            case TransactionRecordMsg -> 36;
            case TransactionMountMsg -> 42;
            default -> null;
        };
        return offset == null ? null : pubkeyAt(offset);
    }

    private MemberSignature member(String signer, int signatureOffset, int pubkeyOffset) {
        byte[] signature = Arrays.copyOfRange(stored, signatureOffset, signatureOffset + RS_SIGNATURE_BYTES);
        byte[] pubkey = pubkeyAt(pubkeyOffset);
        return new MemberSignature(signer, signature, pubkey);
    }

    private byte[] pubkeyAt(int offset) {
        return Arrays.copyOfRange(stored, offset, offset + COMPRESSED_PUBLIC_KEY_BYTES);
    }

    private void requireType(MsgTypeEnum expected) {
        if (type != expected) {
            throw new IllegalStateException("字段不适用于消息类型 " + type + "，期望 " + expected);
        }
    }

    /** 大端序视图（与写入侧 ByteBuffer 默认一致）。 */
    private ByteBuffer buffer() {
        return ByteBuffer.wrap(stored);
    }
}
