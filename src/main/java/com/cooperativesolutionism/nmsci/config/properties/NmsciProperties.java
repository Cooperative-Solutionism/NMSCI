package com.cooperativesolutionism.nmsci.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class NmsciProperties {

    private CentralKeyPair centralKeyPair = new CentralKeyPair();
    private int blockVersion;
    private int blockHeaderSize;
    private long blockMaxSize;
    private long blockDatMaxSize;
    private int registerDifficultyTargetNbits;
    private int transactionDifficultyTargetNbits;
    private String fileRootDir;
    private String fileDatDir;
    private String fileSourceCodeDir;
    private String sourceCodeZipHash;

    public CentralKeyPair getCentralKeyPair() {
        return centralKeyPair;
    }

    public void setCentralKeyPair(CentralKeyPair centralKeyPair) {
        this.centralKeyPair = centralKeyPair;
    }

    public String getCentralPubkeyBase64() {
        return centralKeyPair.getPubkey();
    }

    public String getCentralPrikeyBase64() {
        return centralKeyPair.getPrikey();
    }

    public int getBlockVersion() {
        return blockVersion;
    }

    public void setBlockVersion(int blockVersion) {
        this.blockVersion = blockVersion;
    }

    public int getBlockHeaderSize() {
        return blockHeaderSize;
    }

    public void setBlockHeaderSize(int blockHeaderSize) {
        this.blockHeaderSize = blockHeaderSize;
    }

    public long getBlockMaxSize() {
        return blockMaxSize;
    }

    public void setBlockMaxSize(long blockMaxSize) {
        this.blockMaxSize = blockMaxSize;
    }

    public long getBlockDatMaxSize() {
        return blockDatMaxSize;
    }

    public void setBlockDatMaxSize(long blockDatMaxSize) {
        this.blockDatMaxSize = blockDatMaxSize;
    }

    public int getRegisterDifficultyTargetNbits() {
        return registerDifficultyTargetNbits;
    }

    public void setRegisterDifficultyTargetNbits(int registerDifficultyTargetNbits) {
        this.registerDifficultyTargetNbits = registerDifficultyTargetNbits;
    }

    public int getTransactionDifficultyTargetNbits() {
        return transactionDifficultyTargetNbits;
    }

    public void setTransactionDifficultyTargetNbits(int transactionDifficultyTargetNbits) {
        this.transactionDifficultyTargetNbits = transactionDifficultyTargetNbits;
    }

    public String getFileRootDir() {
        return fileRootDir;
    }

    public void setFileRootDir(String fileRootDir) {
        this.fileRootDir = fileRootDir;
    }

    public String getFileDatDir() {
        return fileDatDir;
    }

    public void setFileDatDir(String fileDatDir) {
        this.fileDatDir = fileDatDir;
    }

    public String getFileSourceCodeDir() {
        return fileSourceCodeDir;
    }

    public void setFileSourceCodeDir(String fileSourceCodeDir) {
        this.fileSourceCodeDir = fileSourceCodeDir;
    }

    public String getSourceCodeZipHash() {
        return sourceCodeZipHash;
    }

    public void setSourceCodeZipHash(String sourceCodeZipHash) {
        this.sourceCodeZipHash = sourceCodeZipHash;
    }

    public static class CentralKeyPair {
        private String pubkey;
        private String prikey;

        public String getPubkey() {
            return pubkey;
        }

        public void setPubkey(String pubkey) {
            this.pubkey = pubkey;
        }

        public String getPrikey() {
            return prikey;
        }

        public void setPrikey(String prikey) {
            this.prikey = prikey;
        }
    }
}
