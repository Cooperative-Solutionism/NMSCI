package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CentralPubkeyLockedMsgRepository extends JpaRepository<CentralPubkeyLockedMsg, UUID> {

    boolean existsByCentralPubkey(byte[] centralPubkey);

    CentralPubkeyLockedMsg findByCentralPubkey(byte[] centralPubkey);

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);

}
