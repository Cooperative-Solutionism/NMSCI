package com.cooperativesolutionism.nmsci.util;

import java.nio.ByteBuffer;
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
     * 字节数组转换为short类型
     *
     * @param bytes  字节数组
     * @param offset 偏移量
     * @param length 长度
     * @return short值
     */
    public static short bytesToShort(byte[] bytes, int offset, int length) {
        return ByteBuffer.wrap(bytes, offset, length).getShort();
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
}
