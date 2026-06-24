package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.enumeration.ConsumeChainNodeFilter;
import com.cooperativesolutionism.nmsci.model.ConsumeChain;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface ConsumeChainRepository extends JpaRepository<ConsumeChain, UUID> {

    /**
     * 为交易挂载分配锁定开放消费链：在加锁层过滤 is_loop=false，优先 start==target（延伸后即成环，
     * 提升成环概率）、其次按 tail_mount_timestamp 升序，用窗口累计和只取「刚好够覆盖 amount」的最小前缀，
     * 并对选中行加 FOR UPDATE 悲观写锁。不足部分由 {@code ConsumeChainAllocator} 新建链承接。
     * 相比原分页循环，将 round-trip 从 O(候选链数/批大小) 降为 1，悲观锁语义不变。
     * 注意：窗口子查询与外层 ORDER BY 必须保持完全一致，否则「最小前缀选中的集合」与「分配器消费顺序」
     * 不匹配，会破坏「刚好够覆盖」不变式。
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
                                   order by (cc.start = :targetId) desc, cc.tail_mount_timestamp, cc.id
                                   rows between unbounded preceding and 1 preceding
                               ), 0) as prior_sum
                        from consume_chains cc
                        where cc."end" = :endId
                            and cc.currency_type = :currencyType
                            and cc.is_loop = false
                    ) s
                    where s.prior_sum < :amount
                )
            order by (c.start = :targetId) desc, c.tail_mount_timestamp, c.id
            for update
            """, nativeQuery = true)
    List<ConsumeChain> lockOpenChainsForAllocation(
            @Param("endId") UUID endId,
            @Param("currencyType") Short currencyType,
            @Param("amount") long amount,
            @Param("targetId") UUID targetId
    );

    @Query("select c from ConsumeChain c where c.id in (select e.chain.id from ConsumeChainEdge e where e.relatedTransactionMount = :relatedTransactionMount)")
    Slice<ConsumeChain> findDistinctByRelatedTransactionMount(TransactionMountMsg relatedTransactionMount, Pageable pageable);

    default Slice<ConsumeChain> findByNodeFilter(
            ConsumeChainNodeFilter filter,
            FlowNodeRegisterMsg node,
            Boolean isLoop,
            Pageable pageable
    ) {
        Objects.requireNonNull(filter, "filter");
        Objects.requireNonNull(node, "node");
        return switch (filter) {
            case START -> findByStartAndOptionalLoop(node, isLoop, pageable);
            case END -> findByEndAndOptionalLoop(node, isLoop, pageable);
            case NODE -> findByNodeAndOptionalLoop(node, isLoop, pageable);
        };
    }

    @Query("""
            select c
            from ConsumeChain c
            where (:isLoop is null or c.isLoop = :isLoop)
                and c.start = :node
            """)
    Slice<ConsumeChain> findByStartAndOptionalLoop(
            @Param("node") FlowNodeRegisterMsg node,
            @Param("isLoop") Boolean isLoop,
            Pageable pageable
    );

    @Query("""
            select c
            from ConsumeChain c
            where (:isLoop is null or c.isLoop = :isLoop)
                and c.end = :node
            """)
    Slice<ConsumeChain> findByEndAndOptionalLoop(
            @Param("node") FlowNodeRegisterMsg node,
            @Param("isLoop") Boolean isLoop,
            Pageable pageable
    );

    @Query("""
            select distinct c
            from ConsumeChain c
            where (:isLoop is null or c.isLoop = :isLoop)
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
    Slice<ConsumeChain> findByNodeAndOptionalLoop(
            @Param("node") FlowNodeRegisterMsg node,
            @Param("isLoop") Boolean isLoop,
            Pageable pageable
    );
}
