package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ConsumeChainRepository extends JpaRepository<ConsumeChain, UUID> {

    /**
     * 为交易挂载分配锁定开放消费链：在加锁层过滤 is_loop=false，按 tail_mount_timestamp 升序，
     * 用窗口累计和只取「刚好够覆盖 amount」的最小前缀，并对选中行加 FOR UPDATE 悲观写锁。
     * 不足部分由 {@code ConsumeChainAllocator} 新建链承接。
     * 相比原分页循环，将 round-trip 从 O(候选链数/批大小) 降为 1，悲观锁与排序语义不变。
     */
    @Query(value = """
            select c.*
            from consume_chains c
            where c."end" = :endId
                and c.currency_type = :currencyType
                and c.is_loop = false
                and c.id in (
                    select s.id
                    from (
                        select cc.id,
                               coalesce(sum(cc.amount) over (
                                   order by cc.tail_mount_timestamp, cc.id
                                   rows between unbounded preceding and 1 preceding
                               ), 0) as prior_sum
                        from consume_chains cc
                        where cc."end" = :endId
                            and cc.currency_type = :currencyType
                            and cc.is_loop = false
                    ) s
                    where s.prior_sum < :amount
                )
            order by c.tail_mount_timestamp, c.id
            for update
            """, nativeQuery = true)
    List<ConsumeChain> lockOpenChainsForAllocation(
            @Param("endId") UUID endId,
            @Param("currencyType") Short currencyType,
            @Param("amount") long amount
    );

    @Query("select c from ConsumeChain c where c.id in (select e.chain.id from ConsumeChainEdge e where e.relatedTransactionMount = :relatedTransactionMount)")
    Slice<ConsumeChain> findDistinctByRelatedTransactionMount(TransactionMountMsg relatedTransactionMount, Pageable pageable);

    Slice<ConsumeChain> findByStart(FlowNodeRegisterMsg start, Pageable pageable);

    Slice<ConsumeChain> findByStartAndIsLoop(FlowNodeRegisterMsg start, Boolean isLoop, Pageable pageable);

    Slice<ConsumeChain> findByEnd(FlowNodeRegisterMsg end, Pageable pageable);

    Slice<ConsumeChain> findByEndAndIsLoop(FlowNodeRegisterMsg end, Boolean isLoop, Pageable pageable);

    @Query("""
            select c
            from ConsumeChain c
            where c.start = :node
                or c.end = :node
                or exists (
                    select 1
                    from ConsumeChainEdge e
                    where e.chain = c
                        and (e.source = :node or e.target = :node)
                )
            """)
    Slice<ConsumeChain> findDistinctByNode(FlowNodeRegisterMsg node, Pageable pageable);

    @Query("""
            select c
            from ConsumeChain c
            where c.isLoop = :isLoop
                and (
                    c.start = :node
                    or c.end = :node
                    or exists (
                        select 1
                        from ConsumeChainEdge e
                        where e.chain = c
                            and (e.source = :node or e.target = :node)
                    )
                )
            """)
    Slice<ConsumeChain> findDistinctByNodeAndIsLoop(FlowNodeRegisterMsg node, Boolean isLoop, Pageable pageable);
}
