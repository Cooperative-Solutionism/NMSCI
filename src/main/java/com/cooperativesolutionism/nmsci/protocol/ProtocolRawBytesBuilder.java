package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.UUID;

@Component
public class ProtocolRawBytesBuilder {

    private static final int UUID_BYTES = 16;
    private static final int PUBKEY_BYTES = 33;
    private static final int CENTRAL_PUBKEY_EMPOWER_VERIFY_DATA_SIZE = Short.BYTES + UUID_BYTES + PUBKEY_BYTES + PUBKEY_BYTES;
    private static final int FLOW_NODE_LOCKED_VERIFY_DATA_SIZE = CENTRAL_PUBKEY_EMPOWER_VERIFY_DATA_SIZE;
    private static final int FLOW_NODE_REGISTER_VERIFY_DATA_SIZE = Short.BYTES + UUID_BYTES + Integer.BYTES + Integer.BYTES + PUBKEY_BYTES;
    private static final int TRANSACTION_RECORD_VERIFY_DATA_SIZE = Short.BYTES + UUID_BYTES + Long.BYTES + Short.BYTES
            + Integer.BYTES + Integer.BYTES + PUBKEY_BYTES + PUBKEY_BYTES + PUBKEY_BYTES;
    private static final int TRANSACTION_MOUNT_VERIFY_DATA_SIZE = Short.BYTES + UUID_BYTES + UUID_BYTES
            + Integer.BYTES + Integer.BYTES + PUBKEY_BYTES + PUBKEY_BYTES + PUBKEY_BYTES;

    public byte[] flowNodeRegisterVerifyData(FlowNodeRegisterMsg msg) {
        return ByteBuffer.allocate(FLOW_NODE_REGISTER_VERIFY_DATA_SIZE)
                .putShort(msg.getMsgType())
                .put(uuidBytes(msg.getId()))
                .putInt(msg.getRegisterDifficultyTarget())
                .putInt(msg.getNonce())
                .put(msg.getFlowNodePubkey())
                .array();
    }

    public byte[] centralPubkeyEmpowerVerifyData(CentralPubkeyEmpowerMsg msg) {
        return ByteBuffer.allocate(CENTRAL_PUBKEY_EMPOWER_VERIFY_DATA_SIZE)
                .putShort(msg.getMsgType())
                .put(uuidBytes(msg.getId()))
                .put(msg.getFlowNodePubkey())
                .put(msg.getCentralPubkey())
                .array();
    }

    public byte[] flowNodeLockedVerifyData(FlowNodeLockedMsg msg) {
        return ByteBuffer.allocate(FLOW_NODE_LOCKED_VERIFY_DATA_SIZE)
                .putShort(msg.getMsgType())
                .put(uuidBytes(msg.getId()))
                .put(msg.getFlowNodePubkey())
                .put(msg.getCentralPubkey())
                .array();
    }

    public byte[] transactionRecordVerifyData(TransactionRecordMsg msg) {
        return ByteBuffer.allocate(TRANSACTION_RECORD_VERIFY_DATA_SIZE)
                .putShort(msg.getMsgType())
                .put(uuidBytes(msg.getId()))
                .putLong(msg.getAmount())
                .putShort(msg.getCurrencyType())
                .putInt(msg.getTransactionDifficultyTarget())
                .putInt(msg.getNonce())
                .put(msg.getConsumeNodePubkey())
                .put(msg.getFlowNodePubkey())
                .put(msg.getCentralPubkey())
                .array();
    }

    public byte[] transactionMountVerifyData(TransactionMountMsg msg) {
        return ByteBuffer.allocate(TRANSACTION_MOUNT_VERIFY_DATA_SIZE)
                .putShort(msg.getMsgType())
                .put(uuidBytes(msg.getId()))
                .put(uuidBytes(msg.getMountedTransactionRecordId()))
                .putInt(msg.getTransactionDifficultyTarget())
                .putInt(msg.getNonce())
                .put(msg.getConsumeNodePubkey())
                .put(msg.getFlowNodePubkey())
                .put(msg.getCentralPubkey())
                .array();
    }

    public byte[] centralSignData(byte[] verifyData, long timestamp, byte[]... signatures) {
        int length = verifyData.length + Long.BYTES;
        for (byte[] signature : signatures) {
            length += signature.length;
        }

        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.put(verifyData);
        for (byte[] signature : signatures) {
            buffer.put(signature);
        }
        buffer.putLong(timestamp);
        return buffer.array();
    }

    private byte[] uuidBytes(UUID uuid) {
        return ByteBuffer.allocate(UUID_BYTES)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }
}
