package com.cooperativesolutionism.nmsci.dto;

public class LockedMessageResponseDTO<T> {

    private boolean locked;
    private T lockedMsg;

    public LockedMessageResponseDTO(boolean locked, T lockedMsg) {
        this.locked = locked;
        this.lockedMsg = lockedMsg;
    }

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
