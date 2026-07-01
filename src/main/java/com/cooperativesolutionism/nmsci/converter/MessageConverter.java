package com.cooperativesolutionism.nmsci.converter;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.Message;

/**
 * 协议消息字节解析器统一契约。
 * <p>
 * 每种协议消息类型对应一个实现，负责将固定长度的入站字节切片为对应的消息实体。
 * 入站字节长度（{@link #expectedSize()}）是中心签名 <em>之前</em> 的协议字节数，
 * 与 {@link MsgTypeEnum#getSize()}（落库/上链时的最终字节数，含中心签名与确认时间戳）不同，
 * 二者均属协议定义，不可混用。
 *
 * @param <T> 解析出的消息实体类型
 */
public interface MessageConverter<T extends Message> {

    /**
     * 该转换器负责的消息类型。
     */
    MsgTypeEnum msgType();

    /**
     * 入站字节的精确长度（中心签名前），用于统一的长度校验。
     */
    int expectedSize();

    /**
     * 将入站字节解析为消息实体。
     *
     * @param byteData 入站字节，长度必须等于 {@link #expectedSize()}
     * @return 解析后的消息实体
     * @throws IllegalArgumentException 当字节为 {@code null} 或长度不符
     */
    T fromByteArray(byte[] byteData);
}
