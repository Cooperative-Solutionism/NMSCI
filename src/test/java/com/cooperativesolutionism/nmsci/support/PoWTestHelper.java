package com.cooperativesolutionism.nmsci.support;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PoWUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;

import java.math.BigInteger;
import java.util.function.IntFunction;

public final class PoWTestHelper {

    private PoWTestHelper() {
    }

    public static int findNonce(int nbits, IntFunction<byte[]> verifyDataFactory) {
        BigInteger target = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(nbits));
        for (int nonce = 0; nonce < Integer.MAX_VALUE; nonce++) {
            byte[] verifyData = verifyDataFactory.apply(nonce);
            BigInteger hash = new BigInteger(1, Sha256Util.doubleDigest(verifyData));
            if (hash.compareTo(target) < 0) {
                return nonce;
            }
        }
        throw new IllegalStateException("No valid nonce found for test difficulty " + nbits);
    }
}
