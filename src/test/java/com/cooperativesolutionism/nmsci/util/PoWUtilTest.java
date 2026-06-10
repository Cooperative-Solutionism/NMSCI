package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PoWUtilTest {

    @Test
    void calculatesTargetFromCompactNBits() {
        BigInteger target = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.hexToBytes("20ffffff"));

        assertEquals("ffffff0000000000000000000000000000000000000000000000000000000000", String.format("%064x", target));
    }

    @Test
    void convertsTargetBackToNBits() {
        BigInteger target = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.hexToBytes("20ffffff"));

        assertArrayEquals(ByteArrayUtil.hexToBytes("20ffffff"), PoWUtil.calculateNBitsFromTarget(target));
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> PoWUtil.calculateTargetFromNBits(new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> PoWUtil.calculateNBitsFromTarget(BigInteger.ZERO));
    }
}
