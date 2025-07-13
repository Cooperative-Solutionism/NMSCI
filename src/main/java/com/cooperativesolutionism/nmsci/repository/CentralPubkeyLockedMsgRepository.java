package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CentralPubkeyLockedMsgRepository extends JpaRepository<CentralPubkeyLockedMsg, UUID> {

    boolean existsByCentralPubkey(byte[] centralPubkey);

    CentralPubkeyLockedMsg findByCentralPubkey(byte[] centralPubkey);

}
