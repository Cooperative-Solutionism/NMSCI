package com.cooperativesolutionism.nmsci.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256Util {

    /**
     * 每线程复用一个 SHA-256 的 {@link MessageDigest} 实例。
     * MessageDigest 非线程安全，故以 ThreadLocal 做线程隔离；复用实例可避免每次 getInstance 的 provider 查找开销，
     * 从而真正受益于 HotSpot 的 SHA-256 intrinsic。使用默认（SUN）provider，输出为标准 SHA-256，与原 BouncyCastle 实现逐字节一致。
     */
    private static final ThreadLocal<MessageDigest> SHA_256 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    });

    /**
     * 计算给定数据的SHA-256哈希值。
     *
     * @param data 要计算哈希的数据
     * @return 计算得到的SHA-256哈希值
     */
    public static byte[] digest(byte[] data) {
        MessageDigest digest = SHA_256.get();
        digest.reset();
        return digest.digest(data);
    }

    /**
     * 计算给定数据的双重SHA-256哈希值。
     *
     * @param data 要计算哈希的数据
     * @return 计算得到的双重SHA-256哈希值
     */
    public static byte[] doubleDigest(byte[] data) {
        return digest(digest(data));
    }
}
