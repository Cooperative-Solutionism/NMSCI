package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

import java.util.UUID;

public interface FlowNodeLockedMsgService {

    /**
     * 保存流转节点冻结消息
     *
     * @param flowNodeLockedMsg 流转节点冻结消息
     * @return 保存后的流转节点冻结消息
     */
    FlowNodeLockedMsg saveFlowNodeLockedMsg(@Valid @Nonnull FlowNodeLockedMsg flowNodeLockedMsg);

    /**
     * 根据ID获取流转节点冻结消息
     *
     * @param id 流转节点冻结消息ID
     * @return 流转节点冻结消息
     */
    FlowNodeLockedMsg getFlowNodeLockedMsgById(UUID id);

    /**
     * 根据流转节点公钥获取流转节点冻结消息
     *
     * @param flowNodePubkey 流转节点公钥
     * @return 流转节点冻结消息
     */
    FlowNodeLockedMsg getFlowNodeLockedMsgByFlowNodePubkey(byte[] flowNodePubkey);

}
