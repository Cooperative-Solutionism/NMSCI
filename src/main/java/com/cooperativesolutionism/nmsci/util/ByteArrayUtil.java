package com.cooperativesolutionism.nmsci.util;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

public class ByteArrayUtil {

    /**
     * short类型转换为字节数组
     *
     * @param value short值
     * @return 字节数组
     */
    public static byte[] shortToBytes(short value) {
        return ByteBuffer.allocate(2).putShort(value).array();
    }

    /**
     * short类型转换为小端序字节数组
     *
     * @param value short值
     * @return 小端序字节数组
     */
    public static byte[] shortToBytesLE(short value) {
        return ByteArrayUtil.reverseBytes(shortToBytes(value));
    }

    /**
     * 字节数组转换为short类型
     *
     * @param bytes 字节数组
     * @return short值
     */
    public static short bytesToShort(byte[] bytes) {
        if (bytes == null || bytes.length != 2) {
            throw new IllegalArgumentException("字节数组必须为2字节长度");
        }
        return ByteBuffer.wrap(bytes).getShort();
    }

    /**
     * int类型转换为字节数组
     *
     * @param value int值
     * @return 字节数组
     */
    public static byte[] intToBytes(int value) {
        return ByteBuffer.allocate(4).putInt(value).array();
    }

    /**
     * int类型转换为小端序字节数组
     *
     * @param value int值
     * @return 小端序字节数组
     */
    public static byte[] intToBytesLE(int value) {
        return ByteArrayUtil.reverseBytes(intToBytes(value));
    }

    /**
     * 字节数组转换为int类型
     *
     * @param bytes 字节数组
     * @return int值
     */
    public static int bytesToInt(byte[] bytes) {
        if (bytes == null || bytes.length != 4) {
            throw new IllegalArgumentException("字节数组必须为4字节长度");
        }
        return ByteBuffer.wrap(bytes).getInt();
    }

    /**
     * long类型转换为字节数组
     *
     * @param value long值
     * @return 字节数组
     */
    public static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }

    /**
     * long类型转换为小端序字节数组
     *
     * @param value long值
     * @return 小端序字节数组
     */
    public static byte[] longToBytesLE(long value) {
        return ByteArrayUtil.reverseBytes(longToBytes(value));
    }

    /**
     * 字节数组转换为long类型
     *
     * @param bytes 字节数组
     * @return long值
     */
    public static long bytesToLong(byte[] bytes) {
        if (bytes == null || bytes.length != 8) {
            throw new IllegalArgumentException("字节数组必须为8字节长度");
        }
        return ByteBuffer.wrap(bytes).getLong();
    }

    /**
     * UUID转换为字节数组
     *
     * @param uuid UUID对象
     * @return 字节数组
     */
    public static byte[] uuidToBytes(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID不能为空");
        }
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer.putLong(uuid.getMostSignificantBits());
        byteBuffer.putLong(uuid.getLeastSignificantBits());
        return byteBuffer.array();
    }

    /**
     * UUID转换为小端序字节数组
     *
     * @param uuid UUID对象
     * @return 小端序字节数组
     */
    public static byte[] uuidToBytesLE(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID不能为空");
        }
        return ByteArrayUtil.reverseBytes(uuidToBytes(uuid));
    }

    /**
     * 字节数组转换为UUID
     *
     * @param bytes 字节数组
     * @return UUID对象
     */
    public static UUID bytesToUUID(byte[] bytes) {
        if (bytes == null || bytes.length != 16) {
            throw new IllegalArgumentException("字节数组必须为16字节长度");
        }
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        long mostSigBits = byteBuffer.getLong();
        long leastSigBits = byteBuffer.getLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    /**
     * 将十六进制字符串转换为字节数组
     *
     * @param hexString 十六进制字符串
     * @return 字节数组
     */
    public static byte[] hexToBytes(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0) {
            throw new IllegalArgumentException("十六进制字符串必须为偶数长度");
        }

        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length(); i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return bytes;
    }

    /**
     * 将十六进制字符串转换为小端序字节数组
     *
     * @param hexString 十六进制字符串
     * @return 小端序字节数组
     */
    public static byte[] hexToBytesLE(String hexString) {
        return ByteArrayUtil.reverseBytes(hexToBytes(hexString));
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 要转换的字节数组
     * @return 十六进制字符串表示
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    /**
     * 将Base64转换为字节数组
     *
     * @param base64String Base64编码的字符串
     * @return 字节数组
     */
    public static byte[] base64ToBytes(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            throw new IllegalArgumentException("Base64字符串不能为空");
        }
        return Base64.getDecoder().decode(base64String);
    }

    /**
     * 将Base64字符串转换为小端序字节数组
     */
    public static byte[] base64ToBytesLE(String base64String) {
        return ByteArrayUtil.reverseBytes(base64ToBytes(base64String));
    }

    /**
     * 将字节数组转换为Base64字符串
     *
     * @param bytes 要转换的字节数组
     * @return Base64编码的字符串
     */
    public static String bytesToBase64(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("字节数组不能为空");
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * 翻转字节序
     *
     * @param bytes 要翻转的字节数组
     * @return 翻转后的字节数组
     */
    public static byte[] reverseBytes(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("字节数组不能为空");
        }
        byte[] reversed = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            reversed[i] = bytes[bytes.length - 1 - i];
        }
        return reversed;
    }
}
