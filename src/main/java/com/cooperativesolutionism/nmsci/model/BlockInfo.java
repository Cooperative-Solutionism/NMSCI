package com.cooperativesolutionism.nmsci.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

@Comment("区块信息")
@Entity
@Table(name = "block_infos")
public class BlockInfo {
    @Id
    @Comment("本区块头的dblsha256hash")
    @Column(name = "id", nullable = false)
    private byte[] id;

    @ColumnDefault("1")
    @Column(name = "version", nullable = false)
    private int version;

    @Comment("区块高度")
    @Column(name = "height", nullable = false)
    private Long height;

    @Comment("相应版本全代码压缩包(包含协议内容)的sha256hash")
    @Column(name = "source_code_zip_hash", nullable = false)
    private byte[] sourceCodeZipHash;

    @Comment("前区块头的dblsha256hash")
    @Column(name = "previous_block_hash", nullable = false)
    private byte[] previousBlockHash;

    @Comment("所有信息的默克尔根")
    @Column(name = "merkle_root", nullable = false)
    private byte[] merkleRoot;

    @Comment("信息内的最大时间戳，单位微秒，时区UTC+0")
    @Column(name = "max_msg_timestamp", nullable = false)
    private Long maxMsgTimestamp;

    @Comment("注册难度目标")
    @Column(name = "register_difficulty_target", nullable = false)
    private Integer registerDifficultyTarget;

    @Comment("交易难度目标")
    @Column(name = "transaction_difficulty_target", nullable = false)
    private Integer transactionDifficultyTarget;

    @Comment("中心公钥")
    @Column(name = "central_pubkey", nullable = false)
    private byte[] centralPubkey;

    @Comment("区块固定时间，单位微秒，时区UTC+0")
    @Column(name = "\"timestamp\"", nullable = false)
    private Long timestamp;

    @Comment("中心签名")
    @Column(name = "central_signature", nullable = false)
    private byte[] centralSignature;

    @Comment("保存区块的dat文件的文件路径")
    @Column(name = "dat_filepath", nullable = false, length = Integer.MAX_VALUE)
    private String datFilepath;

    @Comment("相应版本全代码(包含协议文本)压缩包的文件路径")
    @Column(name = "source_code_zip_filepath", nullable = false, length = Integer.MAX_VALUE)
    private String sourceCodeZipFilepath;

    public String getSourceCodeZipFilepath() {
        return sourceCodeZipFilepath;
    }

    public void setSourceCodeZipFilepath(String sourceCodeZipFilepath) {
        this.sourceCodeZipFilepath = sourceCodeZipFilepath;
    }

    public String getDatFilepath() {
        return datFilepath;
    }

    public void setDatFilepath(String datFilepath) {
        this.datFilepath = datFilepath;
    }

    public byte[] getId() {
        return id;
    }

    public void setId(byte[] id) {
        this.id = id;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Long getHeight() {
        return height;
    }

    public void setHeight(Long height) {
        this.height = height;
    }

    public byte[] getSourceCodeZipHash() {
        return sourceCodeZipHash;
    }

    public void setSourceCodeZipHash(byte[] sourceCodeZipHash) {
        this.sourceCodeZipHash = sourceCodeZipHash;
    }

    public byte[] getPreviousBlockHash() {
        return previousBlockHash;
    }

    public void setPreviousBlockHash(byte[] previousBlockHash) {
        this.previousBlockHash = previousBlockHash;
    }

    public byte[] getMerkleRoot() {
        return merkleRoot;
    }

    public void setMerkleRoot(byte[] merkleRoot) {
        this.merkleRoot = merkleRoot;
    }

    public Long getMaxMsgTimestamp() {
        return maxMsgTimestamp;
    }

    public void setMaxMsgTimestamp(Long maxMsgTimestamp) {
        this.maxMsgTimestamp = maxMsgTimestamp;
    }

    public Integer getRegisterDifficultyTarget() {
        return registerDifficultyTarget;
    }

    public void setRegisterDifficultyTarget(Integer registerDifficultyTarget) {
        this.registerDifficultyTarget = registerDifficultyTarget;
    }

    public Integer getTransactionDifficultyTarget() {
        return transactionDifficultyTarget;
    }

    public void setTransactionDifficultyTarget(Integer transactionDifficultyTarget) {
        this.transactionDifficultyTarget = transactionDifficultyTarget;
    }

    public byte[] getCentralPubkey() {
        return centralPubkey;
    }

    public void setCentralPubkey(byte[] centralPubkey) {
        this.centralPubkey = centralPubkey;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] getCentralSignature() {
        return centralSignature;
    }

    public void setCentralSignature(byte[] centralSignature) {
        this.centralSignature = centralSignature;
    }

}