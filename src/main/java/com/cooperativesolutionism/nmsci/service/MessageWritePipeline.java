package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Service
public class MessageWritePipeline {

    private final MsgAbstractService msgAbstractService;
    private final EntityManager entityManager;

    public MessageWritePipeline(MsgAbstractService msgAbstractService, EntityManager entityManager) {
        this.msgAbstractService = msgAbstractService;
        this.entityManager = entityManager;
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

    // 性能审计 QW2：写入路径实体均为新构造瞬时态，用 EntityManager.persist 直接 INSERT，省去 JpaRepository.save
    // 对赋值主键实体的 select-before-insert（merge）；对 append-only 账本，persist 遇重复主键抛错也比 merge 静默覆盖更安全。
    // 两个方法均由已处于事务内的写服务调用（H1 收窄后的 TransactionTemplate / 挂载写服务 @Transactional）。
    public <M extends Message> M saveAbstractThenEntity(M message) {
        msgAbstractService.saveMsgAbstract(message);
        entityManager.persist(message);
        return message;
    }

    public <M extends Message> M saveEntityThenAbstract(M message) {
        entityManager.persist(message);
        msgAbstractService.saveMsgAbstract(message);
        return message;
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
