package com.cooperativesolutionism.nmsci.support;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

public record TestKeyPair(byte[] pubkey, byte[] prikey) {

    public String pubkeyBase64() {
        return ByteArrayUtil.bytesToBase64(pubkey);
    }

    public String prikeyBase64() {
        return ByteArrayUtil.bytesToBase64(prikey);
    }
}
