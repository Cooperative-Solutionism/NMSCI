package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

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
}
