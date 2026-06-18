package com.cooperativesolutionism.nmsci.constant;

public final class ProtocolByteLengths {

    private ProtocolByteLengths() {
    }

    public static final int UUID_BYTES = 16;
    public static final int RAW_PRIVATE_KEY_BYTES = 32;
    public static final int COMPRESSED_PUBLIC_KEY_BYTES = 33;
    public static final int RS_SIGNATURE_BYTES = 64;

    /** 区块头固定字节数（协议冻结）。与 {@code ParsedBlock.HEADER_SIZE} 同值，详见 PROTOCOL.md。 */
    public static final int BLOCK_HEADER_BYTES = 229;

    public static final int FLOW_NODE_REGISTER_INBOUND_BYTES = 123;
    public static final int CENTRAL_PUBKEY_EMPOWER_INBOUND_BYTES = 148;
    public static final int CENTRAL_PUBKEY_LOCKED_INBOUND_BYTES = 115;
    public static final int FLOW_NODE_LOCKED_INBOUND_BYTES = 148;
    public static final int TRANSACTION_RECORD_INBOUND_BYTES = 263;
    public static final int TRANSACTION_MOUNT_INBOUND_BYTES = 269;

    public static final int FLOW_NODE_REGISTER_STORED_BYTES = 123;
    public static final int CENTRAL_PUBKEY_EMPOWER_STORED_BYTES = 220;
    public static final int CENTRAL_PUBKEY_LOCKED_STORED_BYTES = 187;
    public static final int FLOW_NODE_LOCKED_STORED_BYTES = 220;
    public static final int TRANSACTION_RECORD_STORED_BYTES = 335;
    public static final int TRANSACTION_MOUNT_STORED_BYTES = 341;
}
