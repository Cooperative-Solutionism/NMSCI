package com.cooperativesolutionism.nmsci.protocol;

public interface FlowNodeStateOverview {

    boolean getRegistered();

    boolean getAuthorized();

    boolean getLocked();

    boolean getCurrentCentralPubkeyAuthorized();
}
