package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 {@link SourceCodeArchiveStore} 对「已落盘源码包」的完整性校验（M3）：绑定真实哈希时逐次比对、
 * 不一致即失败、全零占位哈希时跳过。首次复制+原子 move 路径由出块集成测试端到端覆盖。
 */
class SourceCodeArchiveStoreTest {

    private static final int VERSION = 1;
    private static final String ALL_ZERO_HASH = "0000000000000000000000000000000000000000000000000000000000000000";
    private static final byte[] ARCHIVE_BYTES = "nmsci-source-archive-bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path appRoot;

    @Test
    void acceptsExistingArchiveMatchingBoundHash() throws IOException {
        Path archive = writeExistingArchive(ARCHIVE_BYTES);
        SourceCodeArchiveStore store = store(hashOf(ARCHIVE_BYTES));

        String filename = store.copyArchiveForVersion(VERSION);

        assertEquals(archive.getFileName().toString(), filename);
        // 已存在且哈希匹配：文件不被改写
        assertArrayEquals(ARCHIVE_BYTES, Files.readAllBytes(archive));
    }

    @Test
    void rejectsExistingArchiveMismatchingBoundHash() throws IOException {
        writeExistingArchive(ARCHIVE_BYTES);
        // 配置一个非全零但与实际不符的哈希，模拟落盘源码包被篡改/损坏
        String wrongButBoundHash = "1111111111111111111111111111111111111111111111111111111111111111";
        SourceCodeArchiveStore store = store(wrongButBoundHash);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> store.copyArchiveForVersion(VERSION)
        );

        assertTrue(exception.getMessage().contains("完整性校验失败"), exception.getMessage());
    }

    @Test
    void skipsVerificationForAllZeroPlaceholderHash() throws IOException {
        // 占位零哈希=未绑定真实源码：即便内容与任何真实哈希都不符，也不应触发校验失败
        writeExistingArchive(ARCHIVE_BYTES);
        SourceCodeArchiveStore store = store(ALL_ZERO_HASH);

        String filename = store.copyArchiveForVersion(VERSION);

        assertEquals("source_code_v" + VERSION + ".zip", filename);
    }

    private Path writeExistingArchive(byte[] bytes) throws IOException {
        Path archive = appRoot.resolve("file").resolve("source-code").resolve("source_code_v" + VERSION + ".zip");
        Files.createDirectories(archive.getParent());
        Files.write(archive, bytes);
        return archive;
    }

    private SourceCodeArchiveStore store(String sourceCodeZipHash) {
        NmsciProperties properties = new NmsciProperties();
        properties.setFileRootDir("file");
        properties.setFileSourceCodeDir("source-code");
        properties.setSourceCodeZipHash(sourceCodeZipHash);
        return new SourceCodeArchiveStore(properties, appRoot);
    }

    private static String hashOf(byte[] bytes) {
        return ByteArrayUtil.bytesToHex(Sha256Util.digest(bytes));
    }
}
