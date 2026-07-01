package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatBlockReaderTest {

    @Test
    void emptyBytesYieldNoBlocks() {
        assertTrue(DatBlockReader.readConcatenated(new byte[0], "blk00000000.dat").isEmpty());
    }

    @Test
    void rejectsIncompleteRecordPrefix() {
        BlockFormatException ex = assertThrows(BlockFormatException.class,
                () -> DatBlockReader.readConcatenated(new byte[]{0x6A, 0x46, 0x6D}, "blk.dat"));
        assertTrue(ex.getMessage().contains("记录前缀"));
    }

    @Test
    void rejectsWrongMagic() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(0x12345678);
        buffer.putLong(229L);
        assertThrows(BlockFormatException.class,
                () -> DatBlockReader.readConcatenated(buffer.array(), "blk.dat"));
    }

    @Test
    void rejectsRawLengthSmallerThanHeader() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(BlockConstants.MAGIC_NUMBER);
        buffer.putLong(10L);
        BlockFormatException ex = assertThrows(BlockFormatException.class,
                () -> DatBlockReader.readConcatenated(buffer.array(), "blk.dat"));
        assertTrue(ex.getMessage().contains("小于区块头"));
    }

    @Test
    void rejectsBodyExceedingFileBoundary() {
        ByteBuffer buffer = ByteBuffer.allocate(12 + 50);
        buffer.putInt(BlockConstants.MAGIC_NUMBER);
        buffer.putLong(300L); // 声明 300 字节但只跟随 50 字节
        BlockFormatException ex = assertThrows(BlockFormatException.class,
                () -> DatBlockReader.readConcatenated(buffer.array(), "blk.dat"));
        assertTrue(ex.getMessage().contains("超出文件边界"));
    }

    @Test
    void readingMissingDirectoryYieldsNoBlocks() {
        assertEquals(0, DatBlockReader.readDirectory(java.nio.file.Path.of("target", "no-such-dat-dir-xyz")).size());
    }
}
