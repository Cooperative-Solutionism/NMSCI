package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateOverview;

public class FlowNodeStateResponseDTO {

    private boolean registered;
    private boolean authorized;
    private boolean locked;
    private boolean currentCentralPubkeyAuthorized;

    public static FlowNodeStateResponseDTO from(FlowNodeStateOverview overview) {
        FlowNodeStateResponseDTO response = new FlowNodeStateResponseDTO();
        response.setRegistered(overview.getRegistered());
        response.setAuthorized(overview.getAuthorized());
        response.setLocked(overview.getLocked());
        response.setCurrentCentralPubkeyAuthorized(overview.getCurrentCentralPubkeyAuthorized());
        return response;
    }

    public boolean getRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    public boolean getAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public boolean getLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean getCurrentCentralPubkeyAuthorized() {
        return currentCentralPubkeyAuthorized;
    }

    public void setCurrentCentralPubkeyAuthorized(boolean currentCentralPubkeyAuthorized) {
        this.currentCentralPubkeyAuthorized = currentCentralPubkeyAuthorized;
    }
}
