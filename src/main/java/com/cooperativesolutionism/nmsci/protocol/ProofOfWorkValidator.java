package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PoWUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

@Component
public class ProofOfWorkValidator {

    public void validate(byte[] verifyData, int nbits, String errorMessage) {
        BigInteger target = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(nbits));
        BigInteger verifyDataHash = new BigInteger(1, Sha256Util.doubleDigest(verifyData));
        if (verifyDataHash.compareTo(target) > 0) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
