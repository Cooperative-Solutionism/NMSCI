package com.cooperativesolutionism.nmsci.model;

import java.util.UUID;

public interface Message {
    UUID getId();

    void setId(UUID id);

    Short getMsgType();

    void setMsgType(Short msgType);

    Long getConfirmTimestamp();

    void setConfirmTimestamp(Long confirmTimestamp);

    byte[] getRawBytes();

    void setRawBytes(byte[] rawBytes);

    byte[] getTxid();

    void setTxid(byte[] msgType);

}
