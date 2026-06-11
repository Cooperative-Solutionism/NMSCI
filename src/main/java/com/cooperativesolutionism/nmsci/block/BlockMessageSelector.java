package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BlockMessageSelector {

    private static final int PAGE_SIZE = 1000;

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private MsgAbstractRepository msgAbstractRepository;

    public SelectedBlockMessages select() {
        long blockSize = nmsciProperties.getBlockHeaderSize();
        blockSize += MsgTypeEnum.values().length * BlockConstants.MESSAGE_COUNT_FIELD_SIZE;

        long maxMsgTimestamp = 0L;
        Map<MsgTypeEnum, List<MsgAbstract>> selectedMessages = new LinkedHashMap<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            selectedMessages.put(msgType, new ArrayList<>());
        }

        Long lastConfirmTimestamp = null;
        byte[] lastId = null;
        outerLoop:
        while (true) {
            List<MsgAbstract> msgAbstracts = msgAbstractRepository.findNextNotInBlockBatch(lastConfirmTimestamp, lastId, PAGE_SIZE);
            if (msgAbstracts.isEmpty()) {
                break;
            }

            for (MsgAbstract msgAbstract : msgAbstracts) {
                MsgTypeEnum msgType = MsgTypeEnum.getByValue(msgAbstract.getMsgType());
                if (msgType == null) {
                    throw new IllegalArgumentException("未知信息类型: " + msgAbstract.getMsgType());
                }

                blockSize += msgType.getSize();
                if (blockSize > nmsciProperties.getBlockMaxSize()) {
                    break outerLoop;
                }

                maxMsgTimestamp = Math.max(maxMsgTimestamp, msgAbstract.getConfirmTimestamp());
                selectedMessages.get(msgType).add(msgAbstract);
            }

            MsgAbstract lastMsgAbstract = msgAbstracts.get(msgAbstracts.size() - 1);
            lastConfirmTimestamp = lastMsgAbstract.getConfirmTimestamp();
            lastId = lastMsgAbstract.getId();
        }

        return new SelectedBlockMessages(selectedMessages, maxMsgTimestamp);
    }
}
