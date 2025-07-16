package com.cooperativesolutionism.nmsci.model;

public interface Message extends Identifiable {

    Short getMsgType();

    void setMsgType(Short msgType);

    Long getConfirmTimestamp();

    void setConfirmTimestamp(Long confirmTimestamp);

    byte[] getRawBytes();

    void setRawBytes(byte[] rawBytes);

    byte[] getTxid();

    void setTxid(byte[] msgType);

}
