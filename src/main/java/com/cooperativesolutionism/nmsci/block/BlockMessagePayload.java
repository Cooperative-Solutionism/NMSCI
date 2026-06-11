package com.cooperativesolutionism.nmsci.block;

import java.util.UUID;

public interface BlockMessagePayload {

    UUID getId();

    byte[] getRawBytes();

    byte[] getTxid();
}
