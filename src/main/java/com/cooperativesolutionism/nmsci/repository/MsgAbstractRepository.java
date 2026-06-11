package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MsgAbstractRepository extends JpaRepository<MsgAbstract, byte[]> {

    @Query(nativeQuery = true, value = """
            select *
            from msg_abstracts
            where is_in_block = false
                and (
                    :lastConfirmTimestamp is null
                    or confirm_timestamp > :lastConfirmTimestamp
                    or (confirm_timestamp = :lastConfirmTimestamp and id > :lastId)
                )
            order by confirm_timestamp, id
            limit :limit
            """)
    List<MsgAbstract> findNextNotInBlockBatch(
            @Param("lastConfirmTimestamp") Long lastConfirmTimestamp,
            @Param("lastId") byte[] lastId,
            @Param("limit") int limit
    );

    long countByIsInBlockFalseOrderByConfirmTimestampAsc();

}
