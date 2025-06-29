package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionMountMsgRepository extends JpaRepository<TransactionMountMsg, UUID> {
}
