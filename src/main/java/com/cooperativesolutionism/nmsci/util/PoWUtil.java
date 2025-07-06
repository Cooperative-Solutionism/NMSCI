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
            throw new IllegalArgumentException("nBits必须为4字节数组");
        }

        int exponent = nBits[0] & 0xFF;
        int mantissa = ((nBits[1] & 0xFF) << 16) | ((nBits[2] & 0xFF) << 8) | (nBits[3] & 0xFF);
        return BigInteger.valueOf(mantissa).multiply(BigInteger.valueOf(256).pow(exponent - 3));
    }

    /**
     * 从目标值（Target）计算nBits字节数组
     *
     * @param target 目标值
     * @return 计算得到的nBits字节数组
     */
    public static byte[] calculateNBitsFromTarget(BigInteger target) {
        if (target == null || target.signum() <= 0) {
            throw new IllegalArgumentException("目标值必须为正数");
        }

        byte[] targetBytes = target.toByteArray();
        int length = targetBytes[0] == 0 ? targetBytes.length - 1 : targetBytes.length;
        int mantissa;
        int offset = targetBytes[0] == 0 ? 1 : 0;
        if (length >= 3) {
            mantissa = ((targetBytes[offset] & 0xFF) << 16) | ((targetBytes[offset + 1] & 0xFF) << 8) | (targetBytes[offset + 2] & 0xFF);
        } else if (length == 2) {
            mantissa = ((targetBytes[offset] & 0xFF) << 8) | (targetBytes[offset + 1] & 0xFF);
            mantissa <<= 8;
        } else {
            mantissa = (targetBytes[offset] & 0xFF) << 16;
        }
        byte[] nBits = new byte[4];
        nBits[0] = (byte) length;
        nBits[1] = (byte) ((mantissa >> 16) & 0xFF);
        nBits[2] = (byte) ((mantissa >> 8) & 0xFF);
        nBits[3] = (byte) (mantissa & 0xFF);
        return nBits;
    }
}
