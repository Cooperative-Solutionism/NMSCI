package com.cooperativesolutionism.nmsci.repository;

import java.util.UUID;

public interface MessagePayloadProjection {

    UUID getId();

    byte[] getRawBytes();

    byte[] getTxid();
}
