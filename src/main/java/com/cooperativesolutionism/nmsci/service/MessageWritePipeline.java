package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class MessageWritePipeline {

    private final MsgAbstractService msgAbstractService;

    public MessageWritePipeline(MsgAbstractService msgAbstractService) {
        this.msgAbstractService = msgAbstractService;
    }

    public void requireMsgType(Message message, MsgTypeEnum expected) {
        if (message.getMsgType() != expected.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + expected.getValue());
        }
    }

    public void rejectExistingId(Message message, Predicate<UUID> existsById, Supplier<String> conflictMessage) {
        if (existsById.test(message.getId())) {
            throw new ConflictException(conflictMessage.get());
        }
    }

    public <M extends Message> M saveAbstractThenEntity(M message, Function<M, M> saveEntity) {
        msgAbstractService.saveMsgAbstract(message);
        return saveEntity.apply(message);
    }

    public <M extends Message> M saveEntityThenAbstract(M message, Function<M, M> saveEntity) {
        M saved = saveEntity.apply(message);
        msgAbstractService.saveMsgAbstract(message);
        return saved;
    }

    public void populateRawBytes(Message message, byte[] verifyData, byte[]... signatures) {
        int rawBytesLength = verifyData.length;
        for (byte[] signature : signatures) {
            rawBytesLength += signature.length;
        }

        byte[] rawBytes = new byte[rawBytesLength];
        int offset = 0;
        System.arraycopy(verifyData, 0, rawBytes, offset, verifyData.length);
        offset += verifyData.length;
        for (byte[] signature : signatures) {
            System.arraycopy(signature, 0, rawBytes, offset, signature.length);
            offset += signature.length;
        }

        message.setRawBytes(rawBytes);
        message.setTxid(MerkleTreeUtil.calcTxid(rawBytes));
    }
}
