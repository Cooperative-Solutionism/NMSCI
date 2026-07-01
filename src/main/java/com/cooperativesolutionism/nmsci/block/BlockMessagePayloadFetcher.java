package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.MessagePayloadProjection;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
public class BlockMessagePayloadFetcher {

    private final Map<MsgTypeEnum, Function<Collection<UUID>, List<MessagePayloadProjection>>> finders;

    public BlockMessagePayloadFetcher(
            FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository,
            CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository,
            CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository,
            FlowNodeLockedMsgRepository flowNodeLockedMsgRepository,
            TransactionRecordMsgRepository transactionRecordMsgRepository,
            TransactionMountMsgRepository transactionMountMsgRepository
    ) {
        Map<MsgTypeEnum, Function<Collection<UUID>, List<MessagePayloadProjection>>> typedFinders = new EnumMap<>(MsgTypeEnum.class);
        typedFinders.put(MsgTypeEnum.FlowNodeRegisterMsg, flowNodeRegisterMsgRepository::findPayloadByIdIn);
        typedFinders.put(MsgTypeEnum.CentralPubkeyEmpowerMsg, centralPubkeyEmpowerMsgRepository::findPayloadByIdIn);
        typedFinders.put(MsgTypeEnum.CentralPubkeyLockedMsg, centralPubkeyLockedMsgRepository::findPayloadByIdIn);
        typedFinders.put(MsgTypeEnum.FlowNodeLockedMsg, flowNodeLockedMsgRepository::findPayloadByIdIn);
        typedFinders.put(MsgTypeEnum.TransactionRecordMsg, transactionRecordMsgRepository::findPayloadByIdIn);
        typedFinders.put(MsgTypeEnum.TransactionMountMsg, transactionMountMsgRepository::findPayloadByIdIn);
        this.finders = Map.copyOf(typedFinders);
    }

    public List<MessagePayloadProjection> findPayloads(MsgTypeEnum msgType, List<UUID> msgIds) {
        if (msgIds.isEmpty()) {
            return List.of();
        }

        Function<Collection<UUID>, List<MessagePayloadProjection>> finder = finders.get(msgType);
        if (finder == null) {
            return List.of();
        }

        Map<UUID, MessagePayloadProjection> payloadsById = new HashMap<>();
        for (MessagePayloadProjection payload : finder.apply(msgIds)) {
            payloadsById.put(payload.getId(), payload);
        }

        List<MessagePayloadProjection> orderedPayloads = new ArrayList<>();
        for (UUID msgId : msgIds) {
            MessagePayloadProjection payload = payloadsById.get(msgId);
            if (payload == null) {
                throw new IllegalStateException("未找到区块消息正文: " + msgId);
            }
            orderedPayloads.add(payload);
        }

        return orderedPayloads;
    }
}
