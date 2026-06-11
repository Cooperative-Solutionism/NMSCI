package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

@Component
public class ProtocolRawBytesBuilder {

    public byte[] centralPubkeyEmpowerVerifyData(CentralPubkeyEmpowerMsg msg) {
        byte[] verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(msg.getMsgType()),
                ByteArrayUtil.uuidToBytes(msg.getId())
        );
        verifyData = ArrayUtils.addAll(verifyData, msg.getFlowNodePubkey());
        return ArrayUtils.addAll(verifyData, msg.getCentralPubkey());
    }

    public byte[] flowNodeLockedVerifyData(FlowNodeLockedMsg msg) {
        byte[] verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(msg.getMsgType()),
                ByteArrayUtil.uuidToBytes(msg.getId())
        );
        verifyData = ArrayUtils.addAll(verifyData, msg.getFlowNodePubkey());
        return ArrayUtils.addAll(verifyData, msg.getCentralPubkey());
    }

    public byte[] transactionRecordVerifyData(TransactionRecordMsg msg) {
        byte[] verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(msg.getMsgType()),
                ByteArrayUtil.uuidToBytes(msg.getId())
        );
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.longToBytes(msg.getAmount()));
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.shortToBytes(msg.getCurrencyType()));
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.intToBytes(msg.getTransactionDifficultyTarget()));
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.intToBytes(msg.getNonce()));
        verifyData = ArrayUtils.addAll(verifyData, msg.getConsumeNodePubkey());
        verifyData = ArrayUtils.addAll(verifyData, msg.getFlowNodePubkey());
        return ArrayUtils.addAll(verifyData, msg.getCentralPubkey());
    }

    public byte[] transactionMountVerifyData(TransactionMountMsg msg) {
        byte[] verifyData = ArrayUtils.addAll(
                ByteArrayUtil.shortToBytes(msg.getMsgType()),
                ByteArrayUtil.uuidToBytes(msg.getId())
        );
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.uuidToBytes(msg.getMountedTransactionRecordId()));
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.intToBytes(msg.getTransactionDifficultyTarget()));
        verifyData = ArrayUtils.addAll(verifyData, ByteArrayUtil.intToBytes(msg.getNonce()));
        verifyData = ArrayUtils.addAll(verifyData, msg.getConsumeNodePubkey());
        verifyData = ArrayUtils.addAll(verifyData, msg.getFlowNodePubkey());
        return ArrayUtils.addAll(verifyData, msg.getCentralPubkey());
    }

    public byte[] centralSignData(byte[] verifyData, long timestamp, byte[]... signatures) {
        byte[] centralSignData = verifyData;
        for (byte[] signature : signatures) {
            centralSignData = ArrayUtils.addAll(centralSignData, signature);
        }
        return ArrayUtils.addAll(centralSignData, ByteArrayUtil.longToBytes(timestamp));
    }
}
