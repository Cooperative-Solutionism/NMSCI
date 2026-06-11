package com.cooperativesolutionism.nmsci.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByteArrayUtilTest {

    @Test
    void convertsPrimitiveTypesUsingBigEndianOrder() {
        assertArrayEquals(new byte[]{0x12, 0x34}, ByteArrayUtil.shortToBytes((short) 0x1234));
        assertArrayEquals(new byte[]{0x12, 0x34, 0x56, 0x78}, ByteArrayUtil.intToBytes(0x12345678));
        assertEquals((short) 0x1234, ByteArrayUtil.bytesToShort(new byte[]{0x12, 0x34}));
        assertEquals(0x12345678, ByteArrayUtil.bytesToInt(new byte[]{0x12, 0x34, 0x56, 0x78}));
    }

    @Test
    void convertsUuidToBytesAndBack() {
        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        assertEquals(id, ByteArrayUtil.bytesToUUID(ByteArrayUtil.uuidToBytes(id)));
    }

    @Test
    void rejectsInvalidInputLengths() {
        assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.bytesToShort(new byte[]{1}));
        assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.bytesToInt(new byte[]{1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> ByteArrayUtil.bytesToUUID(new byte[15]));
    }

    @Test
    void rejectsNonHexCharactersWhenConvertingHexToBytes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ByteArrayUtil.hexToBytes("0g")
        );

        assertEquals("十六进制字符串包含非法字符", exception.getMessage());
    }
}
