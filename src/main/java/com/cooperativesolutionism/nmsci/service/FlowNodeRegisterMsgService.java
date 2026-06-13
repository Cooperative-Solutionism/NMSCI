package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.dto.FlowNodeStateResponseDTO;
import com.cooperativesolutionism.nmsci.dto.FlowNodeListItemDTO;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

public interface FlowNodeRegisterMsgService {

    /**
     * 保存流转节点注册消息
     *
     * @param flowNodeRegisterMsg 流转节点注册消息
     * @return 保存后的流转节点注册消息
     */
    FlowNodeRegisterMsg saveFlowNodeRegisterMsg(@Valid @Nonnull FlowNodeRegisterMsg flowNodeRegisterMsg);

    /**
     * 根据ID获取流转节点注册消息
     *
     * @param id 流转节点注册消息ID
     * @return 流转节点注册消息
     */
    FlowNodeRegisterMsg getFlowNodeRegisterMsgById(UUID id);

    /**
     * 根据流转节点公钥获取流转节点注册消息
     *
     * @param flowNodePubkey 流转节点公钥
     * @return 流转节点注册消息
     */
    FlowNodeRegisterMsg getFlowNodeRegisterMsgByFlowNodePubkey(byte[] flowNodePubkey);

    /**
     * 查询流转节点当前状态
     *
     * @param flowNodePubkey 流转节点公钥
     * @return 流转节点状态
     */
    FlowNodeStateResponseDTO getFlowNodeState(byte[] flowNodePubkey);

    /**
     * 分页查询流转节点列表
     *
     * @param registered 是否已注册；false时返回空页
     * @param authorized 是否存在任意中心公钥授权；null时不过滤
     * @param locked     是否已冻结；null时不过滤
     * @param pageable   分页参数
     * @return 流转节点列表
     */
    Slice<FlowNodeListItemDTO> listFlowNodes(Boolean registered, Boolean authorized, Boolean locked, Pageable pageable);
}
