package com.cooperativesolutionism.nmsci.verifier;

/**
 * 解析 {@code blk*.dat} 区块二进制时遇到结构性错误（魔数不符、长度前缀不一致、截断、计数越界、
 * 区块体未在声明长度处恰好结束等）。结构性错误意味着该 .dat 不是一条合法序列化的链，验证应判定为不通过。
 */
public class BlockFormatException extends RuntimeException {

    public BlockFormatException(String message) {
        super(message);
    }

    public BlockFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
