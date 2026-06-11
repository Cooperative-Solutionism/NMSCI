package com.cooperativesolutionism.nmsci.constant;

public final class BlockConstants {

    private BlockConstants() {
    }

    // 魔数
    public static final int MAGIC_NUMBER = 0x6A466D85;

    // 创世区块的前hash
    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    public static final int HASH_SIZE = 32;
    public static final int MESSAGE_COUNT_FIELD_SIZE = Long.BYTES;
    public static final int DAT_FILE_INDEX_WIDTH = 8;
    public static final String DAT_FILE_PREFIX = "blk";
    public static final String DAT_FILE_SUFFIX = ".dat";
    public static final String SOURCE_CODE_ZIP_PREFIX = "source_code_v";
    public static final String SOURCE_CODE_ZIP_SUFFIX = ".zip";

}
