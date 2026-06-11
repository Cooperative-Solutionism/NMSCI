package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SelectedBlockMessages {

    private final Map<MsgTypeEnum, List<MsgAbstract>> messagesByType;
    private final long maxMsgTimestamp;

    public SelectedBlockMessages(Map<MsgTypeEnum, List<MsgAbstract>> messagesByType, long maxMsgTimestamp) {
        this.messagesByType = messagesByType;
        this.maxMsgTimestamp = maxMsgTimestamp;
    }

    public Map<MsgTypeEnum, List<MsgAbstract>> getMessagesByType() {
        return messagesByType;
    }

    public List<MsgAbstract> getAllMessages() {
        List<MsgAbstract> messages = new ArrayList<>();
        for (List<MsgAbstract> typedMessages : messagesByType.values()) {
            messages.addAll(typedMessages);
        }
        return Collections.unmodifiableList(messages);
    }

    public boolean isEmpty() {
        for (List<MsgAbstract> typedMessages : messagesByType.values()) {
            if (!typedMessages.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public long getMaxMsgTimestamp() {
        return maxMsgTimestamp;
    }
}
