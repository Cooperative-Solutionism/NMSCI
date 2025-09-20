package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Sha256UtilTest {

    @Test
    void digest() {
//        61d3496a78be856d5564463b61ec1f96fb33bee869a52d12568dc5ccd61e513c
        String hashdata = "000029d9c0c2163c436e91c72967915e67f020ffffff00000000032d9668353fb153d9cbc79fd28eafb2f5fdf4d5e81c330cfb44650feabe571273";
        byte[] hashBytes = ByteArrayUtil.hexToBytes(hashdata);
        byte[] digest = Sha256Util.digest(hashBytes);
        byte[] dblDigest = Sha256Util.doubleDigest(hashBytes);
        System.out.println("ByteArrayUtil.bytesToHex(hashBytes) = " + ByteArrayUtil.bytesToHex(hashBytes));
        System.out.println("ByteArrayUtil.bytesToHex(digest) = " + ByteArrayUtil.bytesToHex(digest));
        System.out.println("ByteArrayUtil.bytesToHex(dblDigest) = " + ByteArrayUtil.bytesToHex(dblDigest));
    }
}