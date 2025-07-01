package com.cooperativesolutionism.nmsci.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Comment;

@Comment("中心可手动配置的参数")
@Entity
@Table(name = "central_configurable_params")
public class CentralConfigurableParam {
    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Comment("相应版本全代码压缩包(包含协议内容)的dblsha256hash")
    @Column(name = "source_code_zip_hash", nullable = false)
    private byte[] sourceCodeZipHash;

    @Comment("注册难度目标")
    @Column(name = "register_difficulty_target", nullable = false)
    private Integer registerDifficultyTarget;

    @Comment("交易难度目标")
    @Column(name = "transaction_difficulty_target", nullable = false)
    private Integer transactionDifficultyTarget;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public byte[] getSourceCodeZipHash() {
        return sourceCodeZipHash;
    }

    public void setSourceCodeZipHash(byte[] sourceCodeZipHash) {
        this.sourceCodeZipHash = sourceCodeZipHash;
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

}