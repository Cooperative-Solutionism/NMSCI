package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeState;
import com.cooperativesolutionism.nmsci.repository.projection.FlowNodeStateOverview;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FlowNodeRegisterMsgRepository extends JpaRepository<FlowNodeRegisterMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);

    FlowNodeRegisterMsg findFirstByFlowNodePubkey(byte[] flowNodePubkey);

    Slice<FlowNodeRegisterMsg> findByFlowNodePubkey(byte[] flowNodePubkey, Pageable pageable);

    @Query(nativeQuery = true, value = """
            select
                exists(select 1 from flow_node_register_msgs where flow_node_pubkey = :flowNodePubkey) as registered,
                exists(select 1 from central_pubkey_empower_msgs where flow_node_pubkey = :flowNodePubkey and central_pubkey = :centralPubkey) as authorized,
                exists(select 1 from flow_node_locked_msgs where flow_node_pubkey = :flowNodePubkey) as locked
            """)
    FlowNodeState findFlowNodeState(
            @Param("flowNodePubkey") byte[] flowNodePubkey,
            @Param("centralPubkey") byte[] centralPubkey
    );

    @Query(nativeQuery = true, value = """
            select
                exists(select 1 from flow_node_register_msgs where flow_node_pubkey = :flowNodePubkey) as registered,
                exists(select 1 from central_pubkey_empower_msgs where flow_node_pubkey = :flowNodePubkey) as authorized,
                exists(select 1 from flow_node_locked_msgs where flow_node_pubkey = :flowNodePubkey) as locked,
                exists(select 1 from central_pubkey_empower_msgs where flow_node_pubkey = :flowNodePubkey and central_pubkey = :currentCentralPubkey) as "currentCentralPubkeyAuthorized"
            """)
    FlowNodeStateOverview findFlowNodeStateOverview(
            @Param("flowNodePubkey") byte[] flowNodePubkey,
            @Param("currentCentralPubkey") byte[] currentCentralPubkey
    );

    @Query("""
            select new com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO(
                f.id,
                f.flowNodePubkey,
                true,
                case when exists (
                    select 1
                    from CentralPubkeyEmpowerMsg empower
                    where empower.flowNodePubkey = f.flowNodePubkey
                ) then true else false end,
                case when exists (
                    select 1
                    from FlowNodeLockedMsg locked
                    where locked.flowNodePubkey = f.flowNodePubkey
                ) then true else false end,
                case when exists (
                    select 1
                    from CentralPubkeyEmpowerMsg currentEmpower
                    where currentEmpower.flowNodePubkey = f.flowNodePubkey
                        and currentEmpower.centralPubkey = :currentCentralPubkey
                ) then true else false end
            )
            from FlowNodeRegisterMsg f
            where (
                :authorized is null
                or (
                    case when exists (
                        select 1
                        from CentralPubkeyEmpowerMsg empowerFilter
                        where empowerFilter.flowNodePubkey = f.flowNodePubkey
                    ) then true else false end
                ) = :authorized
            )
            and (
                :locked is null
                or (
                    case when exists (
                        select 1
                        from FlowNodeLockedMsg lockedFilter
                        where lockedFilter.flowNodePubkey = f.flowNodePubkey
                    ) then true else false end
                ) = :locked
            )
            """)
    Slice<FlowNodeListItemDTO> findFlowNodeList(
            @Param("authorized") Boolean authorized,
            @Param("locked") Boolean locked,
            @Param("currentCentralPubkey") byte[] currentCentralPubkey,
            Pageable pageable
    );

    List<MessagePayloadProjection> findPayloadByIdIn(Collection<UUID> ids);

}
