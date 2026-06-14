package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.model.Message;

/**
 * 转换器基类，统一收敛各消息类型重复的 {@code null}/长度校验，
 * 子类只需声明 {@link #msgType()}、{@link #expectedSize()} 与字段切片逻辑 {@link #decode(byte[])}。
 *
 * @param <T> 解析出的消息实体类型
 */
public abstract class AbstractMessageConverter<T extends Message> implements MessageConverter<T> {

    @Override
    public final T fromByteArray(byte[] byteData) {
        if (byteData == null || byteData.length != expectedSize()) {
            throw new IllegalArgumentException("Invalid byte array size, expected " + expectedSize() + " bytes.");
        }
        return decode(byteData);
    }

    /**
     * 将已通过长度校验的入站字节切片为消息实体。
     *
     * @param byteData 长度已确保等于 {@link #expectedSize()} 的入站字节
     */
    protected abstract T decode(byte[] byteData);
}
