package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.ConfirmableMessage;
import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
public class MsgAbstractService {
    @Resource
    private MsgAbstractRepository msgAbstractRepository;

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
        msgAbstractRepository.save(msgAbstract);
    }
}
