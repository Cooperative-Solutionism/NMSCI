package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;

import java.util.UUID;

public interface CentralPubkeyLockedMsgService {

    /**
     * 保存中心公钥冻结信息
     *
     * @param centralPubkeyLockedMsg 中央公钥冻结信息
     */
    void saveCentralPubkeyLockedMsg(@Valid @Nonnull CentralPubkeyLockedMsg centralPubkeyLockedMsg);

    /**
     * 根据ID获取中心公钥冻结信息
     *
     * @param id 中央公钥冻结信息ID
     * @return 中央公钥冻结信息
     */
    CentralPubkeyLockedMsg getCentralPubkeyLockedMsgById(UUID id);

    /**
     * 根据中心公钥获取中心公钥冻结信息
     *
     * @param centralPubkey 中央公钥字节数组
     * @return 中央公钥冻结信息
     */
    CentralPubkeyLockedMsg getCentralPubkeyLockedMsgByCentralPubkey(byte[] centralPubkey);

}
