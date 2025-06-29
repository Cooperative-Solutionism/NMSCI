package com.cooperativesolutionism.nmsci.util;

import java.math.BigInteger;

public class PoWUtil {

    /**
     * 从nBits字节数组计算难度目标值（Target）
     *
     * @param nBits 4字节的nBits数组
     * @return 计算得到的目标值
     */
    public static BigInteger calculateTargetFromNBits(byte[] nBits) {
        if (nBits == null || nBits.length != 4) {
            throw new IllegalArgumentException("nBits must be a 4-byte array.");
        }

        // 将nBits转换为无符号整数
        BigInteger exponent = BigInteger.valueOf(nBits[0] & 0xFF);
        BigInteger coefficient = new BigInteger(1, new byte[]{nBits[1], nBits[2], nBits[3]});

        // 计算目标值：Target = coefficient * 256^(exponent - 3)
        return coefficient.shiftLeft((int) (exponent.intValue() - 3) * 8);
    }
}
