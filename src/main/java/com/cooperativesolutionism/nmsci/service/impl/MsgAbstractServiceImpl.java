package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.service.MsgAbstractService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class MsgAbstractServiceImpl implements MsgAbstractService {
    @Resource
    private MsgAbstractRepository msgAbstractRepository;

    @Override
    public void saveMsgAbstract(@Valid @Nonnull Message message) {
        MsgAbstract msgAbstract = new MsgAbstract();
        byte[] msgAbstractId = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(message.getMsgType()),
                ByteArrayUtil.uuidToBytes(message.getId())
        );
        msgAbstract.setId(msgAbstractId);
        msgAbstract.setMsgType(message.getMsgType());
        msgAbstract.setMsgId(message.getId());
        msgAbstract.setConfirmTimestamp(message.getConfirmTimestamp());
        msgAbstractRepository.save(msgAbstract);
    }
}
