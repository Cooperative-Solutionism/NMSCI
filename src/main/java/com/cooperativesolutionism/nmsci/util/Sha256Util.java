package com.cooperativesolutionism.nmsci.util;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;

public class Sha256Util {

    /**
     * 计算给定数据的SHA-256哈希值。
     *
     * @param data 要计算哈希的数据
     * @return 计算得到的SHA-256哈希值
     */
    public static byte[] digest(byte[] data) {
        Digest digest = new SHA256Digest();
        digest.update(data, 0, data.length);

        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        return hash;
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
