package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.MessageConverter;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.Message;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 协议消息解析注册表：按 {@link MsgTypeEnum} 收集所有 {@link MessageConverter} Bean，
 * 提供按类型分发解析的统一入口。
 * <p>
 * 构造期做完备性校验——每种消息类型必须恰好注册一个转换器，缺失或重复均在启动时快速失败，
 * 避免新增消息类型时漏配转换器到运行期才暴露。
 */
@Component
public class ProtocolMessageCodec {

    private final Map<MsgTypeEnum, MessageConverter<?>> converters;

    public ProtocolMessageCodec(List<MessageConverter<?>> converterBeans) {
        Map<MsgTypeEnum, MessageConverter<?>> map = new EnumMap<>(MsgTypeEnum.class);
        for (MessageConverter<?> converter : converterBeans) {
            MessageConverter<?> previous = map.put(converter.msgType(), converter);
            if (previous != null) {
                throw new IllegalStateException("消息类型 " + converter.msgType() + " 注册了多个转换器");
            }
        }
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            if (!map.containsKey(msgType)) {
                throw new IllegalStateException("消息类型 " + msgType + " 缺少转换器");
            }
        }
        this.converters = map;
    }

    /**
     * 获取指定消息类型的转换器。
     */
    public MessageConverter<?> converterFor(MsgTypeEnum msgType) {
        MessageConverter<?> converter = converters.get(msgType);
        if (converter == null) {
            throw new IllegalArgumentException("没有为消息类型 " + msgType + " 注册转换器");
        }
        return converter;
    }

    /**
     * 按消息类型将入站字节解析为消息实体。
     */
    public Message decode(MsgTypeEnum msgType, byte[] byteData) {
        return converterFor(msgType).fromByteArray(byteData);
    }
}
