package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestParamParserTest {

    @Test
    void detectsNonBlankValues() {
        assertFalse(RequestParamParser.notBlank(null));
        assertFalse(RequestParamParser.notBlank(""));
        assertFalse(RequestParamParser.notBlank("  "));
        assertTrue(RequestParamParser.notBlank("abc"));
    }

    @Test
    void treatsBlankUuidParameterAsMissing() {
        assertNull(RequestParamParser.uuidOrNull(null));
        assertNull(RequestParamParser.uuidOrNull(""));
        assertNull(RequestParamParser.uuidOrNull("  "));
    }

    @Test
    void parsesRequiredAndOptionalUuidParameters() {
        UUID expected = UUID.fromString("11111111-2222-3333-4444-555555555555");

        assertEquals(expected, RequestParamParser.uuid(expected.toString()));
        assertEquals(expected, RequestParamParser.uuidOrNull(expected.toString()));
    }

    @Test
    void malformedUuidParametersBecomeBadRequest() {
        BadRequestException required = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.uuid("not-a-uuid")
        );
        assertEquals("UUID格式不正确", required.getMessage());

        BadRequestException optional = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.uuidOrNull("not-a-uuid")
        );
        assertEquals("UUID格式不正确", optional.getMessage());
    }

    @Test
    void parsesRequiredAndOptionalHexBytes() {
        assertNull(RequestParamParser.hexBytesOrNull(null));
        assertNull(RequestParamParser.hexBytesOrNull(""));
        assertNull(RequestParamParser.hexBytesOrNull("  "));
        assertArrayEquals(new byte[]{0x00, 0x0f, (byte) 0xab}, RequestParamParser.hexBytes("000fab"));
        assertArrayEquals(new byte[]{0x00, 0x0f, (byte) 0xab}, RequestParamParser.hexBytesOrNull("000fab"));
    }

    @Test
    void parsesCompressedPubkeyHexParametersOnlyWhenLengthMatchesProtocol() {
        String pubkey = "02" + "00".repeat(32);

        assertNull(RequestParamParser.compressedPubkeyHexOrNull(null));
        assertNull(RequestParamParser.compressedPubkeyHexOrNull(""));
        assertNull(RequestParamParser.compressedPubkeyHexOrNull("  "));
        assertArrayEquals(RequestParamParser.hexBytes(pubkey), RequestParamParser.compressedPubkeyHexOrNull(pubkey));
    }

    @Test
    void compressedPubkeyHexParametersRejectWrongLength() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.compressedPubkeyHexOrNull("00")
        );

        assertEquals("公钥长度错误，必须为33字节", exception.getMessage());
    }

    @Test
    void malformedHexParametersBecomeBadRequest() {
        BadRequestException required = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.hexBytes("0g")
        );
        assertEquals("十六进制字符串包含非法字符", required.getMessage());

        BadRequestException optional = assertThrows(
                BadRequestException.class,
                () -> RequestParamParser.hexBytesOrNull("0g")
        );
        assertEquals("十六进制字符串包含非法字符", optional.getMessage());
    }
}
