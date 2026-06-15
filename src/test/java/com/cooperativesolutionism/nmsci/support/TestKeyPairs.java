package com.cooperativesolutionism.nmsci.support;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.bitcoinj.crypto.ECKey;

import java.math.BigInteger;

public final class TestKeyPairs {

    public static final TestKeyPair CENTRAL = fromBase64(
            "A9+yx3Fml7ugoSwhxDH4bUv+O1NrLsC38y5/l7vPsgy+",
            "BIVAd26jw2HNo0izjp8YSSf78cmGneReK0PmD48mRns="
    );

    public static final TestKeyPair FLOW_NODE_A = fromPrivateHex(
            "0000000000000000000000000000000000000000000000000000000000000011"
    );

    public static final TestKeyPair FLOW_NODE_B = fromPrivateHex(
            "0000000000000000000000000000000000000000000000000000000000000012"
    );

    public static final TestKeyPair CONSUME_NODE_A = fromPrivateHex(
            "0000000000000000000000000000000000000000000000000000000000000013"
    );

    private TestKeyPairs() {
    }

    private static TestKeyPair fromBase64(String pubkeyBase64, String prikeyBase64) {
        return new TestKeyPair(
                ByteArrayUtil.base64ToBytes(pubkeyBase64),
                ByteArrayUtil.base64ToBytes(prikeyBase64)
        );
    }

    public static TestKeyPair fromPrivateHex(String privateKeyHex) {
        byte[] prikey = ByteArrayUtil.hexToBytes(privateKeyHex);
        byte[] pubkey = ECKey.fromPrivate(new BigInteger(1, prikey), true).getPubKey();
        return new TestKeyPair(pubkey, prikey);
    }

    /** Derive a unique deterministic test key by index (offset avoids the fixed 0x11/0x12/0x13 fixtures). */
    public static TestKeyPair deriveByIndex(int index) {
        return fromPrivateHex(String.format("%064x", 0x1000 + index));
    }
}
