package com.cooperativesolutionism.nmsci.dto;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;

/**
 * 区块/存储格式相关常量（魔数、创世前哈希、各尺寸上限、dat 与源码包命名约定），供客户端解析与校验。
 */
public class BlockFormatMetadataDTO {

    private int magicNumber;

    private String magicNumberHex;

    private String genesisHash;

    private int hashSize;

    private int messageCountFieldSize;

    private int blockVersion;

    private int blockHeaderSize;

    private long blockMaxSizeBytes;

    private long blockDatMaxSizeBytes;

    private String datFilePrefix;

    private String datFileSuffix;

    private int datFileIndexWidth;

    private String sourceCodeZipPrefix;

    private String sourceCodeZipSuffix;

    public static BlockFormatMetadataDTO from(NmsciProperties properties) {
        BlockFormatMetadataDTO dto = new BlockFormatMetadataDTO();
        dto.setMagicNumber(BlockConstants.MAGIC_NUMBER);
        dto.setMagicNumberHex(String.format("0x%08x", BlockConstants.MAGIC_NUMBER));
        dto.setGenesisHash(BlockConstants.GENESIS_HASH);
        dto.setHashSize(BlockConstants.HASH_SIZE);
        dto.setMessageCountFieldSize(BlockConstants.MESSAGE_COUNT_FIELD_SIZE);
        dto.setBlockVersion(properties.getBlockVersion());
        dto.setBlockHeaderSize(properties.getBlockHeaderSize());
        dto.setBlockMaxSizeBytes(properties.getBlockMaxSize());
        dto.setBlockDatMaxSizeBytes(properties.getBlockDatMaxSize());
        dto.setDatFilePrefix(BlockConstants.DAT_FILE_PREFIX);
        dto.setDatFileSuffix(BlockConstants.DAT_FILE_SUFFIX);
        dto.setDatFileIndexWidth(BlockConstants.DAT_FILE_INDEX_WIDTH);
        dto.setSourceCodeZipPrefix(BlockConstants.SOURCE_CODE_ZIP_PREFIX);
        dto.setSourceCodeZipSuffix(BlockConstants.SOURCE_CODE_ZIP_SUFFIX);
        return dto;
    }

    public int getMagicNumber() {
        return magicNumber;
    }

    public void setMagicNumber(int magicNumber) {
        this.magicNumber = magicNumber;
    }

    public String getMagicNumberHex() {
        return magicNumberHex;
    }

    public void setMagicNumberHex(String magicNumberHex) {
        this.magicNumberHex = magicNumberHex;
    }

    public String getGenesisHash() {
        return genesisHash;
    }

    public void setGenesisHash(String genesisHash) {
        this.genesisHash = genesisHash;
    }

    public int getHashSize() {
        return hashSize;
    }

    public void setHashSize(int hashSize) {
        this.hashSize = hashSize;
    }

    public int getMessageCountFieldSize() {
        return messageCountFieldSize;
    }

    public void setMessageCountFieldSize(int messageCountFieldSize) {
        this.messageCountFieldSize = messageCountFieldSize;
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

    public long getBlockMaxSizeBytes() {
        return blockMaxSizeBytes;
    }

    public void setBlockMaxSizeBytes(long blockMaxSizeBytes) {
        this.blockMaxSizeBytes = blockMaxSizeBytes;
    }

    public long getBlockDatMaxSizeBytes() {
        return blockDatMaxSizeBytes;
    }

    public void setBlockDatMaxSizeBytes(long blockDatMaxSizeBytes) {
        this.blockDatMaxSizeBytes = blockDatMaxSizeBytes;
    }

    public String getDatFilePrefix() {
        return datFilePrefix;
    }

    public void setDatFilePrefix(String datFilePrefix) {
        this.datFilePrefix = datFilePrefix;
    }

    public String getDatFileSuffix() {
        return datFileSuffix;
    }

    public void setDatFileSuffix(String datFileSuffix) {
        this.datFileSuffix = datFileSuffix;
    }

    public int getDatFileIndexWidth() {
        return datFileIndexWidth;
    }

    public void setDatFileIndexWidth(int datFileIndexWidth) {
        this.datFileIndexWidth = datFileIndexWidth;
    }

    public String getSourceCodeZipPrefix() {
        return sourceCodeZipPrefix;
    }

    public void setSourceCodeZipPrefix(String sourceCodeZipPrefix) {
        this.sourceCodeZipPrefix = sourceCodeZipPrefix;
    }

    public String getSourceCodeZipSuffix() {
        return sourceCodeZipSuffix;
    }

    public void setSourceCodeZipSuffix(String sourceCodeZipSuffix) {
        this.sourceCodeZipSuffix = sourceCodeZipSuffix;
    }
}
