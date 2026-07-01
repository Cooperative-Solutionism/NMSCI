package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.serializer.BytesToHexSerializer;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.UUID;

public class FlowNodeListItemDTO {

    private UUID id;

    @JsonSerialize(using = BytesToHexSerializer.class)
    private byte[] flowNodePubkey;

    private boolean registered;

    private boolean authorized;

    private boolean locked;

    private boolean currentCentralPubkeyAuthorized;

    public FlowNodeListItemDTO(
            UUID id,
            byte[] flowNodePubkey,
            boolean registered,
            boolean authorized,
            boolean locked,
            boolean currentCentralPubkeyAuthorized
    ) {
        this.id = id;
        this.flowNodePubkey = flowNodePubkey;
        this.registered = registered;
        this.authorized = authorized;
        this.locked = locked;
        this.currentCentralPubkeyAuthorized = currentCentralPubkeyAuthorized;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public byte[] getFlowNodePubkey() {
        return flowNodePubkey;
    }

    public void setFlowNodePubkey(byte[] flowNodePubkey) {
        this.flowNodePubkey = flowNodePubkey;
    }

    @JsonProperty("registered")
    public boolean getRegistered() {
        return registered;
    }

    public void setRegistered(boolean registered) {
        this.registered = registered;
    }

    @JsonProperty("authorized")
    public boolean getAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    @JsonProperty("locked")
    public boolean getLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @JsonProperty("currentCentralPubkeyAuthorized")
    public boolean getCurrentCentralPubkeyAuthorized() {
        return currentCentralPubkeyAuthorized;
    }

    public void setCurrentCentralPubkeyAuthorized(boolean currentCentralPubkeyAuthorized) {
        this.currentCentralPubkeyAuthorized = currentCentralPubkeyAuthorized;
    }
}
