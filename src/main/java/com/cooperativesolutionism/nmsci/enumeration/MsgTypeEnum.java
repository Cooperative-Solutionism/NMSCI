package com.cooperativesolutionism.nmsci.enumeration;

public enum MsgTypeEnum {
    FlowNodeRegisterMsg((short) 0x0000, 123, 123, "流转节点注册信息"),
    CentralPubkeyEmpowerMsg((short) 0x0001, 220, 148, "中心公钥公证信息"),
    CentralPubkeyLockedMsg((short) 0x0002, 187, 115, "中心公钥冻结信息"),
    FlowNodeLockedMsg((short) 0x0003, 220, 148, "流转节点冻结信息"),
    TransactionRecordMsg((short) 0x0004, 335, 263, "交易记录信息"),
    TransactionMountMsg((short) 0x0005, 341, 269, "交易挂载信息");

    private final short value;
    // 落库/上链时的最终字节数（含中心签名与确认时间戳）
    private final int size;
    // 入站 POST 的字节数（中心签名之前），等于各转换器的 expectedSize()，由 ProtocolMessageCodec 启动期校验一致
    private final int inboundSize;
    private final String name;

    MsgTypeEnum(short value, int size, int inboundSize, String name) {
        this.value = value;
        this.size = size;
        this.inboundSize = inboundSize;
        this.name = name;
    }

    /**
     * 根据消息类型获取该消息类型的size
     *
     * @return 消息类型的值
     */
    public static int getSizeByValue(short value) {
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            if (msgType.getValue() == value) {
                return msgType.getSize();
            }
        }
        return 0;
    }

    /**
     * 根据消息类型获取该消息类型的枚举
     *
     * @param msgType 消息类型
     * @return 消息类型枚举
     */
    public static MsgTypeEnum getByValue(Short msgType) {
        if (msgType == null) {
            return null;
        }

        for (MsgTypeEnum msgTypeEnum : MsgTypeEnum.values()) {
            if (msgTypeEnum.getValue() == msgType) {
                return msgTypeEnum;
            }
        }
        return null;
    }

    public short getValue() {
        return value;
    }

    public int getSize() {
        return size;
    }

    public int getInboundSize() {
        return inboundSize;
    }

    public String getName() {
        return name;
    }
}
