package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.ConfirmableMessage;
import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
public class MsgAbstractService {
    private final MsgAbstractRepository msgAbstractRepository;
    private final EntityManager entityManager;

    public MsgAbstractService(MsgAbstractRepository msgAbstractRepository, EntityManager entityManager) {
        this.msgAbstractRepository = msgAbstractRepository;
        this.entityManager = entityManager;
    }

    /**
     * 待入块（未装入区块）消息数量。
     */
    public long countPending() {
        return msgAbstractRepository.countByIsInBlockFalseOrderByConfirmTimestampAsc();
    }

    /**
     * 最旧的待入块消息的确认时间戳（微秒，UTC+0）；无待入块消息时返回 null。
     */
    public Long findOldestPendingConfirmTimestamp() {
        List<MsgAbstract> oldest = msgAbstractRepository.findNextNotInBlockBatch(null, null, 1);
        return oldest.isEmpty() ? null : oldest.get(0).getConfirmTimestamp();
    }
    public void saveMsgAbstract(@Valid @Nonnull Message message) {
        MsgAbstract msgAbstract = new MsgAbstract();
        byte[] msgAbstractId = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(message.getMsgType()),
                ByteArrayUtil.uuidToBytes(message.getId())
        );
        msgAbstract.setId(msgAbstractId);
        msgAbstract.setMsgType(message.getMsgType());
        msgAbstract.setMsgId(message.getId());
        Long confirmTimestamp = 0L;
        if (message instanceof ConfirmableMessage confirmable) {
            confirmTimestamp = confirmable.getConfirmTimestamp();
        }
        msgAbstract.setConfirmTimestamp(confirmTimestamp);
        // 性能审计 QW2：MsgAbstract 为赋值主键的新构造瞬时态，直接 persist 省去 JpaRepository.save 的
        // select-before-insert（merge）；且对 append-only 账本 persist 遇重复主键抛错比 merge 静默覆盖更安全。
        // 仅由写入管道在事务内调用。
        entityManager.persist(msgAbstract);
    }
}
