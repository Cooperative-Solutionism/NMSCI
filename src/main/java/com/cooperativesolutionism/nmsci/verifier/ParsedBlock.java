package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.util.Sha256Util;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * 从 {@code blk*.dat} 解析出的单个区块：固定 229 字节区块头各字段 + 区块体内的全部消息（按落盘顺序）。
 *
 * <p>区块头字节切片定义两类不同口径，务必区分：
 * <ul>
 *   <li>{@link #headerSigningPreimage()} = 头部 [0,165)，不含中心签名，是中心对区块签名的原文；</li>
 *   <li>{@link #fullHeader()} = 头部 [0,229)，含 64 字节中心签名，其 dblSHA256（不翻转）即 {@link #blockId()}，
 *       作为下一区块的「前区块头摘要」。</li>
 * </ul>
 */
public final class ParsedBlock {

    /** 区块头固定字节数。 */
    public static final int HEADER_SIZE = 229;
    /** 中心签名前的待签名头部字节数。 */
    public static final int HEADER_SIGNING_PREIMAGE_SIZE = 165;

    private static final int OFFSET_VERSION = 0;
    private static final int OFFSET_HEIGHT = 4;
    private static final int OFFSET_SOURCE_CODE_ZIP_HASH = 12;
    private static final int OFFSET_PREVIOUS_BLOCK_HEADER_DIGEST = 44;
    private static final int OFFSET_MERKLE_ROOT = 76;
    private static final int OFFSET_MAX_MSG_TIMESTAMP = 108;
    private static final int OFFSET_REGISTER_DIFFICULTY = 116;
    private static final int OFFSET_TRANSACTION_DIFFICULTY = 120;
    private static final int OFFSET_CENTRAL_PUBKEY = 124;
    private static final int OFFSET_BLOCK_TIMESTAMP = 157;
    private static final int OFFSET_CENTRAL_SIGNATURE = 165;

    private static final int HASH_SIZE = 32;
    private static final int COMPRESSED_PUBKEY_SIZE = 33;
    private static final int SIGNATURE_SIZE = 64;

    private final byte[] rawBlock;
    private final List<ParsedMessage> messages;
    private final byte[] blockId;
    private final String datFileName;

    ParsedBlock(byte[] rawBlock, List<ParsedMessage> messages, String datFileName) {
        this.rawBlock = rawBlock;
        this.messages = List.copyOf(messages);
        this.datFileName = datFileName;
        this.blockId = Sha256Util.doubleDigest(fullHeader());
    }

    public int version() {
        return header().getInt(OFFSET_VERSION);
    }

    public long height() {
        return header().getLong(OFFSET_HEIGHT);
    }

    public byte[] sourceCodeZipHash() {
        return slice(OFFSET_SOURCE_CODE_ZIP_HASH, HASH_SIZE);
    }

    public byte[] previousBlockHeaderDigest() {
        return slice(OFFSET_PREVIOUS_BLOCK_HEADER_DIGEST, HASH_SIZE);
    }

    public byte[] merkleRoot() {
        return slice(OFFSET_MERKLE_ROOT, HASH_SIZE);
    }

    public long maxMsgTimestamp() {
        return header().getLong(OFFSET_MAX_MSG_TIMESTAMP);
    }

    public int registerDifficultyTarget() {
        return header().getInt(OFFSET_REGISTER_DIFFICULTY);
    }

    public int transactionDifficultyTarget() {
        return header().getInt(OFFSET_TRANSACTION_DIFFICULTY);
    }

    public byte[] centralPubkey() {
        return slice(OFFSET_CENTRAL_PUBKEY, COMPRESSED_PUBKEY_SIZE);
    }

    public long blockTimestamp() {
        return header().getLong(OFFSET_BLOCK_TIMESTAMP);
    }

    public byte[] centralSignature() {
        return slice(OFFSET_CENTRAL_SIGNATURE, SIGNATURE_SIZE);
    }

    /** 头部 [0,165)：中心区块签名的待签名原文。 */
    public byte[] headerSigningPreimage() {
        return slice(0, HEADER_SIGNING_PREIMAGE_SIZE);
    }

    /** 头部 [0,229)：含中心签名的完整区块头。 */
    public byte[] fullHeader() {
        return slice(0, HEADER_SIZE);
    }

    /** 本区块 id = dblSHA256(完整区块头)，不翻转；作为下一区块的前区块头摘要。 */
    public byte[] blockId() {
        return blockId.clone();
    }

    public byte[] rawBlock() {
        return rawBlock.clone();
    }

    public List<ParsedMessage> messages() {
        return messages;
    }

    /** 该区块来自的 .dat 文件名（按字节直接解析时可能为 null）。 */
    public String datFileName() {
        return datFileName;
    }

    private byte[] slice(int offset, int length) {
        return Arrays.copyOfRange(rawBlock, offset, offset + length);
    }

    private ByteBuffer header() {
        return ByteBuffer.wrap(rawBlock, 0, HEADER_SIZE);
    }
}
