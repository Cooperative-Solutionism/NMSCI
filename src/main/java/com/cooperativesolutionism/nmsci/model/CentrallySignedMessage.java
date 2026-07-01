package com.cooperativesolutionism.nmsci.model;

public interface CentrallySignedMessage extends ConfirmableMessage {

    void setCentralSignature(byte[] centralSignature);
}
