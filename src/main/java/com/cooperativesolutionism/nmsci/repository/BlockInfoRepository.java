package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockInfoRepository extends JpaRepository<BlockInfo, byte[]> {

    BlockInfo findTopByOrderByHeightDesc();

}
