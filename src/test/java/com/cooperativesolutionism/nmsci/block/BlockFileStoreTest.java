package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockFileStoreTest {

    @TempDir
    private Path tempDir;

    @Test
    void appendBlockCreatesDatFileAndReturnsFilename() throws Exception {
        BlockFileStore store = new BlockFileStore(properties(1024L), tempDir);
        byte[] block = new byte[]{1, 2, 3};

        String filename = store.appendBlock(null, block);

        assertEquals("blk00000000.dat", filename);
        assertArrayEquals(block, Files.readAllBytes(tempDir.resolve("file/dat/blk00000000.dat")));
    }

    @Test
    void appendBlockAddsBytesWithoutReplacingExistingData() throws Exception {
        BlockFileStore store = new BlockFileStore(properties(1024L), tempDir);
        Path datFile = tempDir.resolve("file/dat/blk00000000.dat");
        Files.createDirectories(datFile.getParent());
        Files.write(datFile, new byte[]{9, 8});

        String filename = store.appendBlock("blk00000000.dat", new byte[]{1, 2, 3});

        assertEquals("blk00000000.dat", filename);
        assertArrayEquals(new byte[]{9, 8, 1, 2, 3}, Files.readAllBytes(datFile));
    }

    @Test
    void appendBlockRotatesWhenCurrentDatFileWouldExceedLimit() throws Exception {
        BlockFileStore store = new BlockFileStore(properties(4L), tempDir);
        Path datDir = tempDir.resolve("file/dat");
        Path currentDatFile = datDir.resolve("blk00000000.dat");
        Files.createDirectories(datDir);
        Files.write(currentDatFile, new byte[]{9, 8});

        String filename = store.appendBlock("blk00000000.dat", new byte[]{1, 2, 3});

        assertEquals("blk00000001.dat", filename);
        assertArrayEquals(new byte[]{9, 8}, Files.readAllBytes(currentDatFile));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(datDir.resolve("blk00000001.dat")));
    }

    @Test
    void appendBlockUsesPlatformPathSeparatorForPreviousDatFilePath() throws Exception {
        BlockFileStore store = new BlockFileStore(properties(4L), tempDir);
        Path datDir = tempDir.resolve("file/dat");
        Path currentDatFile = datDir.resolve("blk00000000.dat");
        Files.createDirectories(datDir);
        Files.write(currentDatFile, new byte[]{9, 8});

        String filename = store.appendBlock(Path.of("dat", "blk00000000.dat").toString(), new byte[]{1, 2, 3});

        assertEquals("blk00000001.dat", filename);
        assertTrue(Files.exists(datDir.resolve("blk00000001.dat")));
    }

    @Test
    void rollbackDeletesDatFileCreatedByCurrentTransaction() throws Exception {
        BlockFileStore store = new BlockFileStore(properties(1024L), tempDir);
        Path datFile = tempDir.resolve("file/dat/blk00000000.dat");
        TransactionSynchronizationManager.initSynchronization();
        try {
            store.appendBlock(null, new byte[]{1, 2, 3});

            rollbackSynchronizations();

            assertFalse(Files.exists(datFile));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rollbackTruncatesDatFileToOriginalLengthWhenTransactionAppendedMultipleBlocks() throws Exception {
        BlockFileStore store = new BlockFileStore(properties(1024L), tempDir);
        Path datFile = tempDir.resolve("file/dat/blk00000000.dat");
        Files.createDirectories(datFile.getParent());
        Files.write(datFile, new byte[]{9, 8});
        TransactionSynchronizationManager.initSynchronization();
        try {
            store.appendBlock("blk00000000.dat", new byte[]{1, 2, 3});
            store.appendBlock("blk00000000.dat", new byte[]{4, 5, 6});

            rollbackSynchronizations();

            assertArrayEquals(new byte[]{9, 8}, Files.readAllBytes(datFile));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void datStorageInfoCountsBlocksUnderConfiguredDatDirectory() {
        BlockFileStore store = new BlockFileStore(properties(1024L), tempDir);

        String filename = store.appendBlock(null, new byte[]{1, 2, 3, 4, 5});

        BlockFileStore.DatStorageInfo info = store.datStorageInfo();

        // 对外只报告配置的相对目录（file/dat），绝不暴露运行目录绝对路径
        assertEquals(Path.of("file", "dat").toString(), info.datDirectory());
        assertFalse(Path.of(info.datDirectory()).isAbsolute());
        assertEquals(1, info.fileCount());
        assertEquals(filename, info.currentFileName());
        assertEquals(5L, info.currentFileSizeBytes());
        assertEquals(5L, info.totalBytes());
        assertEquals(1024L, info.maxSizePerFileBytes());
        // 但统计的是实际写入目录 <root>/file/dat，而非相对 CWD 的 ./dat
        Path datDir = tempDir.resolve("file").resolve("dat");
        assertTrue(Files.exists(datDir.resolve(filename)));
    }

    @Test
    void datStorageInfoReportsConfiguredDirectoryEvenWhenEmpty() {
        BlockFileStore store = new BlockFileStore(properties(1024L), tempDir);

        BlockFileStore.DatStorageInfo info = store.datStorageInfo();

        assertEquals(Path.of("file", "dat").toString(), info.datDirectory());
        assertFalse(Path.of(info.datDirectory()).isAbsolute());
        assertEquals(0, info.fileCount());
        assertNull(info.currentFileName());
        assertEquals(0L, info.totalBytes());
        assertEquals(1024L, info.maxSizePerFileBytes());
    }

    private void rollbackSynchronizations() {
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    private NmsciProperties properties(long blockDatMaxSize) {
        NmsciProperties properties = new NmsciProperties();
        properties.setFileRootDir("file");
        properties.setFileDatDir("dat");
        properties.setBlockDatMaxSize(blockDatMaxSize);
        return properties;
    }
}
