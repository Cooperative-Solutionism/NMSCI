package com.cooperativesolutionism.nmsci.enumeration;

public enum MsgTypeEnum {
    FlowNodeRegisterMsg((short) 0x0000, 123, "流转节点注册信息"),
    CentralPubkeyEmpowerMsg((short) 0x0001, 220, "中心公钥公证信息"),
    CentralPubkeyLockedMsg((short) 0x0002, 187, "中心公钥冻结信息"),
    FlowNodeLockedMsg((short) 0x0003, 220, "流转节点冻结信息"),
    TransactionRecordMsg((short) 0x0004, 335, "交易记录信息"),
    TransactionMountMsg((short) 0x0005, 341, "交易挂载信息");

    private final short value;
    private final int size;
    private final String name;

    MsgTypeEnum(short value, int size, String name) {
        this.value = value;
        this.size = size;
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

    public String getName() {
        return name;
    }
}
