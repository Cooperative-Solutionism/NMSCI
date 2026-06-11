package com.cooperativesolutionism.nmsci.model;

public interface CentrallySignedMessage extends Message {

    void setCentralSignature(byte[] centralSignature);
}
