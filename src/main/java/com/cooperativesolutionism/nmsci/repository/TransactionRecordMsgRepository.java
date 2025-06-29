package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRecordMsgRepository extends JpaRepository<TransactionRecordMsg, UUID> {
}
