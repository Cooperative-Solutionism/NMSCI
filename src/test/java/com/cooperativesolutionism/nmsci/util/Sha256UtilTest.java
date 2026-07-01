package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256UtilTest {

    @Test
    void calculatesKnownSha256AndDoubleSha256() {
        byte[] data = ByteArrayUtil.hexToBytes("000029d9c0c2163c436e91c72967915e67f020ffffff00000000032d9668353fb153d9cbc79fd28eafb2f5fdf4d5e81c330cfb44650feabe571273");

        assertEquals("1c8a76de137e76c4c4e7d6f922bf45758d2ff0212526429e2a9cabaf1c2c1588", ByteArrayUtil.bytesToHex(Sha256Util.digest(data)));
        assertEquals("61d3496a78be856d5564463b61ec1f96fb33bee869a52d12568dc5ccd61e513c", ByteArrayUtil.bytesToHex(Sha256Util.doubleDigest(data)));
    }
}
