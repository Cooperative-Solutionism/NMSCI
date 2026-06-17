package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.converter.MessageConverter;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.Message;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolMessageCodecTest {

    @Test
    void constructorRejectsDuplicateConvertersForSameMessageType() {
        MsgTypeEnum duplicateType = MsgTypeEnum.FlowNodeRegisterMsg;
        List<MessageConverter<?>> converters = new ArrayList<>(completeConvertersWith(
                duplicateType,
                converter(duplicateType, duplicateType.getInboundSize(), new StubMessage())));
        converters.add(converter(duplicateType, duplicateType.getInboundSize(), new StubMessage()));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ProtocolMessageCodec(converters));

        assertEquals("消息类型 " + duplicateType + " 注册了多个转换器", exception.getMessage());
    }

    @Test
    void constructorRejectsMissingConverter() {
        MsgTypeEnum missingType = MsgTypeEnum.TransactionMountMsg;

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ProtocolMessageCodec(completeConvertersExcept(missingType)));

        assertEquals("消息类型 " + missingType + " 缺少转换器", exception.getMessage());
    }

    @Test
    void constructorRejectsConverterSizeMismatch() {
        MsgTypeEnum mismatchType = MsgTypeEnum.CentralPubkeyEmpowerMsg;
        int mismatchedSize = mismatchType.getInboundSize() + 1;

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new ProtocolMessageCodec(completeConvertersWith(
                        mismatchType,
                        converter(mismatchType, mismatchedSize, new StubMessage()))));

        assertEquals("消息类型 " + mismatchType + " 的转换器入站字节数("
                + mismatchedSize + ")与枚举声明的入站字节数(" + mismatchType.getInboundSize() + ")不一致",
                exception.getMessage());
    }

    @Test
    void decodeUsesConverterForMessageType() {
        MsgTypeEnum decodedType = MsgTypeEnum.TransactionRecordMsg;
        byte[] byteData = new byte[decodedType.getInboundSize()];
        Message decodedMessage = new StubMessage();
        ProtocolMessageCodec codec = new ProtocolMessageCodec(completeConvertersWith(
                decodedType,
                converter(decodedType, decodedType.getInboundSize(), decodedMessage)));

        Message result = codec.decode(decodedType, byteData);

        assertSame(decodedMessage, result);
    }

    private List<MessageConverter<?>> completeConvertersExcept(MsgTypeEnum excluded) {
        return Arrays.stream(MsgTypeEnum.values())
                .filter(msgType -> msgType != excluded)
                .<MessageConverter<?>>map(msgType -> converter(msgType, msgType.getInboundSize(), new StubMessage()))
                .toList();
    }

    private List<MessageConverter<?>> completeConvertersWith(
            MsgTypeEnum replacementType,
            MessageConverter<?> replacement) {
        return Arrays.stream(MsgTypeEnum.values())
                .<MessageConverter<?>>map(msgType -> msgType == replacementType
                        ? replacement
                        : converter(msgType, msgType.getInboundSize(), new StubMessage()))
                .toList();
    }

    private MessageConverter<Message> converter(MsgTypeEnum msgType, int expectedSize, Message decoded) {
        return new MessageConverter<>() {
            @Override
            public MsgTypeEnum msgType() {
                return msgType;
            }

            @Override
            public int expectedSize() {
                return expectedSize;
            }

            @Override
            public Message fromByteArray(byte[] byteData) {
                return decoded;
            }
        };
    }

    private static class StubMessage implements Message {

        private UUID id;
        private Short msgType;
        private byte[] rawBytes;
        private byte[] txid;

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public void setId(UUID id) {
            this.id = id;
        }

        @Override
        public Short getMsgType() {
            return msgType;
        }

        @Override
        public void setMsgType(Short msgType) {
            this.msgType = msgType;
        }

        @Override
        public byte[] getRawBytes() {
            return rawBytes;
        }

        @Override
        public void setRawBytes(byte[] rawBytes) {
            this.rawBytes = rawBytes;
        }

        @Override
        public byte[] getTxid() {
            return txid;
        }

        @Override
        public void setTxid(byte[] txid) {
            this.txid = txid;
        }
    }
}
