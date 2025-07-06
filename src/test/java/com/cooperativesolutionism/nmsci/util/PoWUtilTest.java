package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

class PoWUtilTest {

    @Test
    void calculateTargetFromNBits() {
        // 基础难度0x1d00ffff
        int nBits = 0x1f00ffff;
        System.out.println("nBits = " + nBits);
        byte[] bytes = ByteArrayUtil.intToBytes(nBits);
        System.out.println("bytes = " + Arrays.toString(bytes));
        System.out.println("bytes.length = " + bytes.length);
        String s = ByteArrayUtil.bytesToBase64(bytes);
        System.out.println(s);
        BigInteger target = PoWUtil.calculateTargetFromNBits(bytes);
        System.out.println("Target: " + target);
    }
}
