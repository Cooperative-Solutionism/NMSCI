package com.cooperativesolutionism.nmsci.config.properties;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;
import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.RAW_PRIVATE_KEY_BYTES;

import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Base64;

@Validated
@ConfigurationProperties(prefix = "nmsci")
public class NmsciProperties {

    @Valid
    @NotNull(message = "central-key-pair不能为空")
    private CentralKeyPair centralKeyPair = new CentralKeyPair();

    @Min(value = 1, message = "block-version必须为正数")
    private int blockVersion;

    @Positive(message = "block-header-size必须为正数")
    private int blockHeaderSize;

    @Positive(message = "block-max-size必须为正数")
    private long blockMaxSize;

    @Positive(message = "block-dat-max-size必须为正数")
    private long blockDatMaxSize;

    @Positive(message = "register-difficulty-target-nbits必须为正数")
    private int registerDifficultyTargetNbits;

    @Positive(message = "transaction-difficulty-target-nbits必须为正数")
    private int transactionDifficultyTargetNbits;

    @NotBlank(message = "file-root-dir不能为空")
    private String fileRootDir;

    @NotBlank(message = "file-dat-dir不能为空")
    private String fileDatDir;

    @NotBlank(message = "file-source-code-dir不能为空")
    private String fileSourceCodeDir;

    @NotBlank(message = "source-code-zip-hash不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "source-code-zip-hash必须为64位十六进制字符串")
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

    @AssertTrue(message = "block-max-size必须大于等于block-header-size")
    public boolean isBlockMaxSizeValid() {
        if (blockHeaderSize <= 0 || blockMaxSize <= 0) {
            return true;
        }
        return blockMaxSize >= blockHeaderSize;
    }

    @AssertTrue(message = "block-dat-max-size必须至少能容纳一个完整区块")
    public boolean isBlockDatMaxSizeValid() {
        if (blockMaxSize <= 0 || blockDatMaxSize <= 0) {
            return true;
        }
        return blockDatMaxSize - Integer.BYTES - Long.BYTES >= blockMaxSize;
    }

    public static class CentralKeyPair {
        @NotBlank(message = "central-key-pair.pubkey不能为空")
        private String pubkey;

        @NotBlank(message = "central-key-pair.prikey不能为空")
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

        @AssertTrue(message = "central-key-pair.pubkey必须是33字节压缩公钥Base64")
        public boolean isPubkeyValid() {
            byte[] decoded = decodeBase64(pubkey);
            return decoded != null
                    && decoded.length == COMPRESSED_PUBLIC_KEY_BYTES
                    && (decoded[0] == 0x02 || decoded[0] == 0x03);
        }

        @AssertTrue(message = "central-key-pair.prikey必须是32字节私钥Base64")
        public boolean isPrikeyValid() {
            byte[] decoded = decodeBase64(prikey);
            return decoded != null && decoded.length == RAW_PRIVATE_KEY_BYTES;
        }

        @AssertTrue(message = "central-key-pair.pubkey与central-key-pair.prikey不匹配")
        public boolean isKeyPairMatched() {
            byte[] decodedPubkey = decodeBase64(pubkey);
            byte[] decodedPrikey = decodeBase64(prikey);
            if (decodedPubkey == null || decodedPubkey.length != COMPRESSED_PUBLIC_KEY_BYTES
                    || (decodedPubkey[0] != 0x02 && decodedPubkey[0] != 0x03)
                    || decodedPrikey == null || decodedPrikey.length != RAW_PRIVATE_KEY_BYTES) {
                return true;
            }
            try {
                return Arrays.equals(decodedPubkey, Secp256k1EncryptUtil.rawToECKey(decodedPrikey).getPubKey());
            } catch (RuntimeException ex) {
                return false;
            }
        }

        private static byte[] decodeBase64(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }
}
