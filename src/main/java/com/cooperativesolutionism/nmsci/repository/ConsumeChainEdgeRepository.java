package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.ConsumeChainEdge;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ConsumeChainEdgeRepository extends JpaRepository<ConsumeChainEdge, UUID> {

    List<ConsumeChainEdge> findByChain(ConsumeChain chain);

    /**
     * 根据source、target和currencyType查询消费链条边，并且按chain去重，同时related_transaction_mount_timestamp在starttime和endtime之间
     *
     * @param source       边的起点
     * @param target       边的终点
     * @param currencyType 货币类型
     * @param startTime    起始时间戳
     * @param endTime      结束时间戳
     * @return List<ConsumeChainEdge> 符合条件的消费链条边列表
     */
    @Query(nativeQuery = true, value = "SELECT DISTINCT ON (c.chain) c.* FROM consume_chain_edges c WHERE c.source = :source AND c.target = :target AND c.currency_type = :currencyType AND c.related_transaction_mount_timestamp BETWEEN :startTime AND :endTime ORDER BY c.chain, c.related_transaction_mount_timestamp")
    List<ConsumeChainEdge> findConsumeChainEdges(
            UUID source,
            UUID target,
            short currencyType,
            Long startTime,
            Long endTime
    );

    /**
     * 根据target和currencyType查询消费链条边，并且按chain去重，同时related_transaction_mount_timestamp在starttime和endtime之间
     *
     * @param target       边的终点
     * @param currencyType 货币类型
     * @param startTime    起始时间戳
     * @param endTime      结束时间戳
     * @return List<ConsumeChainEdge> 符合条件的消费链条边列表
     */
    @Query(nativeQuery = true, value = "SELECT DISTINCT ON (c.chain) c.* FROM consume_chain_edges c WHERE c.target = :target AND c.currency_type = :currencyType AND c.related_transaction_mount_timestamp BETWEEN :startTime AND :endTime ORDER BY c.chain, c.related_transaction_mount_timestamp")
    List<ConsumeChainEdge> findConsumeChainEdgesByTarget(
            UUID target,
            short currencyType,
            Long startTime,
            Long endTime
    );

    List<ConsumeChainEdge> findByRelatedTransactionMount(TransactionMountMsg relatedTransactionMount);

    List<ConsumeChainEdge> findByChainOrderByRelatedTransactionMountTimestampAsc(ConsumeChain chain);
}
