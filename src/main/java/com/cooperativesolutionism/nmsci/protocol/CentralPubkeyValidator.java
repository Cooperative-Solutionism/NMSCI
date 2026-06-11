package com.cooperativesolutionism.nmsci.protocol;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class CentralPubkeyValidator {

    private final NmsciProperties nmsciProperties;
    private final CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    public CentralPubkeyValidator(
            NmsciProperties nmsciProperties,
            CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository
    ) {
        this.nmsciProperties = nmsciProperties;
        this.centralPubkeyLockedMsgRepository = centralPubkeyLockedMsgRepository;
    }

    public byte[] currentCentralPubkey() {
        return ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPubkeyBase64());
    }

    public void validateCurrentAndNotLocked(byte[] centralPubkey) {
        validateNotLocked(centralPubkey);
        validateCurrent(centralPubkey);
    }

    public void validateNotLocked(byte[] centralPubkey) {
        if (centralPubkeyLockedMsgRepository.existsByCentralPubkey(centralPubkey)) {
            throw new IllegalArgumentException("该中心公钥(" + ByteArrayUtil.bytesToBase64(centralPubkey) + ")已被冻结");
        }
    }

    public void validateCurrent(byte[] centralPubkey) {
        if (!Arrays.equals(centralPubkey, currentCentralPubkey())) {
            throw new IllegalArgumentException("中心公钥设置错误，当前中心公钥为:(" + nmsciProperties.getCentralPubkeyBase64() + ")");
        }
    }
}
