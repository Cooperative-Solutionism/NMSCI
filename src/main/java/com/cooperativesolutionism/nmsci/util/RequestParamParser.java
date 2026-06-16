package com.cooperativesolutionism.nmsci.util;

import java.util.UUID;

public final class RequestParamParser {

    private RequestParamParser() {
    }

    public static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    public static UUID uuidOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return UUID.fromString(value);
    }

    public static byte[] hexBytesOrNull(String value) {
        if (!notBlank(value)) {
            return null;
        }

        return ByteArrayUtil.hexToBytes(value);
    }
}
