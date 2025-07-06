package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MsgAbstractRepository extends JpaRepository<MsgAbstract, byte[]> {

    Page<MsgAbstract> findByIsInBlockFalseOrderByConfirmTimestampAsc(Pageable pageable);

}
