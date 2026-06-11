package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.block.BlockMessagePayload;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeState;
import com.cooperativesolutionism.nmsci.protocol.FlowNodeStateOverview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FlowNodeRegisterMsgRepository extends JpaRepository<FlowNodeRegisterMsg, UUID> {

    boolean existsByFlowNodePubkey(byte[] flowNodePubkey);

    FlowNodeRegisterMsg findFirstByFlowNodePubkey(byte[] flowNodePubkey);

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

    List<BlockMessagePayload> findPayloadByIdIn(Collection<UUID> ids);

}
