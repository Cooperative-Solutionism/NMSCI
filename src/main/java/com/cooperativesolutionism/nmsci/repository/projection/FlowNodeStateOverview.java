package com.cooperativesolutionism.nmsci.repository.projection;

public interface FlowNodeStateOverview {

    boolean getRegistered();

    boolean getAuthorized();

    boolean getLocked();

    boolean getCurrentCentralPubkeyAuthorized();
}
