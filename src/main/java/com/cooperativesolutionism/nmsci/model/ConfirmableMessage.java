package com.cooperativesolutionism.nmsci.model;

public interface ConfirmableMessage extends Message {

    Long getConfirmTimestamp();

    void setConfirmTimestamp(Long confirmTimestamp);

}
