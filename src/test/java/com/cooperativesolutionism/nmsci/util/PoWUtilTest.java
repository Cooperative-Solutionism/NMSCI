package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;

@Disabled
class PoWUtilTest {

    @Test
    void calculateTargetFromNBits() {
        // 基础难度0x1d00ffff
        // 0.07倍基础难度 0x1d0e4916
        // 0.01倍基础难度 0x1d63ff9c
        // 0.001倍基础难度 0x1e03e7fd
        //                0x1f002708 (10000Hash/s时用时约120秒)
        // 0.0001倍基础难度 0x1f00270d (10000Hash/s时用时约5秒)
        // 0难度 0x20ffffff
        int nBits = 0x1d0e4916;
        System.out.println("nBits = " + nBits);
        byte[] bytes = ByteArrayUtil.intToBytes(nBits);
        System.out.println("bytes = " + Arrays.toString(bytes));
        System.out.println("bytes.length = " + bytes.length);
        BigInteger target = PoWUtil.calculateTargetFromNBits(bytes);
        BigInteger target2 = target.divide(BigInteger.valueOf(7)).multiply(BigInteger.valueOf(100));
        System.out.println("Target: " + target);
        System.out.println("Target in hex (padded): " + String.format("%064x", target));
        System.out.println("Target2 in hex (padded): " + String.format("%064x", target2));
        System.out.println("Target2 in nbits: " + ByteArrayUtil.bytesToHex(PoWUtil.calculateNBitsFromTarget(target2)));

        byte[] ff = ByteArrayUtil.hexToBytes("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
        BigInteger target3 = new BigInteger(1, ff);
        System.out.println("Target3 in hex (padded): " + String.format("%064x", target3));
        System.out.println("Target3 in nbits: " + ByteArrayUtil.bytesToHex(PoWUtil.calculateNBitsFromTarget(target3)));
    }
}
