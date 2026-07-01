package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.model.BlockInfoSummary;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

public class SystemParamsDTO {

    private int blockVersion;

    private String centralPubkey;

    private int registerDifficultyTargetNbits;

    private String registerDifficultyTargetNbitsHex;

    private int transactionDifficultyTargetNbits;

    private String transactionDifficultyTargetNbitsHex;

    private String sourceCodeZipHash;

    private Long latestBlockHeight;

    private String latestBlockHash;

    public static SystemParamsDTO from(NmsciProperties properties, BlockInfoSummary latestBlock) {
        SystemParamsDTO dto = new SystemParamsDTO();
        dto.setBlockVersion(properties.getBlockVersion());
        dto.setCentralPubkey(ByteArrayUtil.bytesToHex(ByteArrayUtil.base64ToBytes(properties.getCentralPubkeyBase64())));
        dto.setRegisterDifficultyTargetNbits(properties.getRegisterDifficultyTargetNbits());
        dto.setRegisterDifficultyTargetNbitsHex(hexInt(properties.getRegisterDifficultyTargetNbits()));
        dto.setTransactionDifficultyTargetNbits(properties.getTransactionDifficultyTargetNbits());
        dto.setTransactionDifficultyTargetNbitsHex(hexInt(properties.getTransactionDifficultyTargetNbits()));
        dto.setSourceCodeZipHash(properties.getSourceCodeZipHash());
        if (latestBlock != null) {
            dto.setLatestBlockHeight(latestBlock.getHeight());
            dto.setLatestBlockHash(ByteArrayUtil.bytesToHex(latestBlock.getId()));
        }
        return dto;
    }

    private static String hexInt(int value) {
        return String.format("0x%08x", value);
    }

    public int getBlockVersion() {
        return blockVersion;
    }

    public void setBlockVersion(int blockVersion) {
        this.blockVersion = blockVersion;
    }

    public String getCentralPubkey() {
        return centralPubkey;
    }

    public void setCentralPubkey(String centralPubkey) {
        this.centralPubkey = centralPubkey;
    }

    public int getRegisterDifficultyTargetNbits() {
        return registerDifficultyTargetNbits;
    }

    public void setRegisterDifficultyTargetNbits(int registerDifficultyTargetNbits) {
        this.registerDifficultyTargetNbits = registerDifficultyTargetNbits;
    }

    public String getRegisterDifficultyTargetNbitsHex() {
        return registerDifficultyTargetNbitsHex;
    }

    public void setRegisterDifficultyTargetNbitsHex(String registerDifficultyTargetNbitsHex) {
        this.registerDifficultyTargetNbitsHex = registerDifficultyTargetNbitsHex;
    }

    public int getTransactionDifficultyTargetNbits() {
        return transactionDifficultyTargetNbits;
    }

    public void setTransactionDifficultyTargetNbits(int transactionDifficultyTargetNbits) {
        this.transactionDifficultyTargetNbits = transactionDifficultyTargetNbits;
    }

    public String getTransactionDifficultyTargetNbitsHex() {
        return transactionDifficultyTargetNbitsHex;
    }

    public void setTransactionDifficultyTargetNbitsHex(String transactionDifficultyTargetNbitsHex) {
        this.transactionDifficultyTargetNbitsHex = transactionDifficultyTargetNbitsHex;
    }

    public String getSourceCodeZipHash() {
        return sourceCodeZipHash;
    }

    public void setSourceCodeZipHash(String sourceCodeZipHash) {
        this.sourceCodeZipHash = sourceCodeZipHash;
    }

    public Long getLatestBlockHeight() {
        return latestBlockHeight;
    }

    public void setLatestBlockHeight(Long latestBlockHeight) {
        this.latestBlockHeight = latestBlockHeight;
    }

    public String getLatestBlockHash() {
        return latestBlockHash;
    }

    public void setLatestBlockHash(String latestBlockHash) {
        this.latestBlockHash = latestBlockHash;
    }
}
