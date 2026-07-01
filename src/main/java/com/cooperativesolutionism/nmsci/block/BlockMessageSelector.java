package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BlockMessageSelector {

    private static final int MIN_BATCH_SIZE = 1000;
    private static final int MAX_BATCH_SIZE = 10000;
    private static final int MIN_MESSAGE_SIZE = Arrays.stream(MsgTypeEnum.values())
            .mapToInt(MsgTypeEnum::getSize)
            .min()
            .orElse(1);

    private final NmsciProperties nmsciProperties;
    private final MsgAbstractRepository msgAbstractRepository;

    public BlockMessageSelector(NmsciProperties nmsciProperties, MsgAbstractRepository msgAbstractRepository) {
        this.nmsciProperties = nmsciProperties;
        this.msgAbstractRepository = msgAbstractRepository;
    }

    public SelectedBlockMessages select() {
        long blockSize = nmsciProperties.getBlockHeaderSize();
        blockSize += MsgTypeEnum.values().length * BlockConstants.MESSAGE_COUNT_FIELD_SIZE;

        int batchSize = selectionBatchSize(blockSize);
        long maxMsgTimestamp = 0L;
        Map<MsgTypeEnum, List<MsgAbstract>> selectedMessages = new LinkedHashMap<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            selectedMessages.put(msgType, new ArrayList<>());
        }

        Long lastConfirmTimestamp = null;
        byte[] lastId = null;
        outerLoop:
        while (true) {
            List<MsgAbstract> msgAbstracts = msgAbstractRepository.findNextNotInBlockBatch(lastConfirmTimestamp, lastId, batchSize);
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

    /**
     * 按区块体可用字节预算估算单次取数批量：一个区块最多容纳 预算/最小消息字节 条消息，
     * 多取 1 条用于触发溢出判定，使典型区块一次取数即可选满，减少大区块时的 round-trip；
     * 下限 {@value #MIN_BATCH_SIZE} 保证小 block-max-size 配置下与既有分批行为一致。
     */
    private int selectionBatchSize(long reservedBytes) {
        long budget = nmsciProperties.getBlockMaxSize() - reservedBytes;
        if (budget <= 0) {
            return MIN_BATCH_SIZE;
        }
        long maxMessages = budget / MIN_MESSAGE_SIZE + 1;
        return (int) Math.max(MIN_BATCH_SIZE, Math.min(MAX_BATCH_SIZE, maxMessages));
    }
}
