package com.cooperativesolutionism.nmsci.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LockedMessageResponseDTO<T> {

    private boolean locked;
    private T lockedMsg;

    public LockedMessageResponseDTO(boolean locked, T lockedMsg) {
        this.locked = locked;
        this.lockedMsg = lockedMsg;
    }

    @JsonProperty("locked")
    public boolean getLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public T getLockedMsg() {
        return lockedMsg;
    }

    public void setLockedMsg(T lockedMsg) {
        this.lockedMsg = lockedMsg;
    }
}
