package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

import java.util.UUID;

public interface CentralPubkeyEmpowerMsgService {

    /**
     * 保存中心公钥授权消息
     *
     * @param centralPubkeyEmpowerMsg 中心公钥授权消息对象
     * @return 保存后的中心公钥授权消息对象
     */
    CentralPubkeyEmpowerMsg saveCentralPubkeyEmpowerMsg(@Valid @Nonnull CentralPubkeyEmpowerMsg centralPubkeyEmpowerMsg);

    /**
     * 根据ID获取中心公钥授权消息
     *
     * @param id 中心公钥授权消息ID
     * @return 中心公钥授权消息对象
     */
    CentralPubkeyEmpowerMsg getCentralPubkeyEmpowerMsgById(UUID id);

    /**
     * 根据FlowNode公钥获取中心公钥授权消息
     *
     * @param flowNodePubkey FlowNode公钥
     * @return 中心公钥授权消息对象
     */
    CentralPubkeyEmpowerMsg getCentralPubkeyEmpowerMsgByFlowNodePubkey(byte[] flowNodePubkey);

}
