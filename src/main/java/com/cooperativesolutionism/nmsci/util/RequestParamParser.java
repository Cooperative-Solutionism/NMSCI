package com.cooperativesolutionism.nmsci.util;

import com.cooperativesolutionism.nmsci.exception.BadRequestException;

import java.util.UUID;

public final class RequestParamParser {

    private static final String INVALID_UUID_MESSAGE = "UUID格式不正确";

    private RequestParamParser() {
    }

    public static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException e) {
            throw new BadRequestException(INVALID_UUID_MESSAGE);
        }
    }

    public static UUID uuidOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return uuid(value);
    }

    public static byte[] hexBytes(String value) {
        try {
            return ByteArrayUtil.hexToBytes(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static byte[] hexBytesOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return hexBytes(value);
    }
}
