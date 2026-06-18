package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class SourceCodeArchiveStore {

    private final NmsciProperties nmsciProperties;
    private final Path applicationRoot;

    @Autowired
    public SourceCodeArchiveStore(NmsciProperties nmsciProperties) {
        this(nmsciProperties, Path.of(System.getProperty("user.dir")));
    }

    SourceCodeArchiveStore(NmsciProperties nmsciProperties, Path applicationRoot) {
        this.nmsciProperties = nmsciProperties;
        this.applicationRoot = applicationRoot;
    }

    /**
     * 确保版本对应的源码包已落盘并通过完整性校验，返回其文件名。
     *
     * <p>不存在则从类路径 {@code static/} 资源以「临时文件 + 原子 move」方式发布，避免半写入的源码包被信任；
     * 随后（仅当配置了真实的源码哈希时）将落盘文件的单次 SHA-256 与 {@code nmsci.source-code-zip-hash} 比对，
     * 不一致即判为完整性失败并抛出。当配置为全零占位哈希（构建前/测试，未绑定真实源码）时跳过校验。
     */
    public String copyArchiveForVersion(int blockVersion) {
        String sourceCodeZipFilename = BlockConstants.SOURCE_CODE_ZIP_PREFIX + blockVersion + BlockConstants.SOURCE_CODE_ZIP_SUFFIX;
        Path sourceCodePath = applicationRoot
                .resolve(nmsciProperties.getFileRootDir())
                .resolve(nmsciProperties.getFileSourceCodeDir())
                .resolve(sourceCodeZipFilename);

        try {
            if (!Files.exists(sourceCodePath)) {
                publishFromClasspath(sourceCodeZipFilename, sourceCodePath);
            }
            verifyArchiveHash(sourceCodePath, sourceCodeZipFilename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy source code zip file", e);
        }

        return sourceCodePath.getFileName().toString();
    }

    /** 以临时文件 + 原子 move 从类路径资源发布源码包，确保读取方永远看不到半写入的文件。 */
    private void publishFromClasspath(String sourceCodeZipFilename, Path sourceCodePath) throws IOException {
        // 临时文件放在专用 .staging 子目录（与目标同卷，原子 move 仍可用），避免 .tmp 出现在被消费的源码目录里，
        // 杜绝任何按目录枚举的逻辑偶发看到半成品文件。
        Path stagingDir = sourceCodePath.getParent().resolve(".staging");
        Files.createDirectories(stagingDir);
        Path tempFile = Files.createTempFile(stagingDir, sourceCodeZipFilename + ".", ".tmp");
        try {
            try (InputStream inputStream = openArchiveResource(sourceCodeZipFilename)) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            atomicMove(tempFile, sourceCodePath);
        } finally {
            // move 成功后临时文件已不存在，此处为失败路径的清理，避免遗留 .tmp。
            Files.deleteIfExists(tempFile);
        }
    }

    /** 打开类路径中的源码包资源。提取为可重写方法，便于单测以已知字节替换类路径读取（不依赖构建期生成的真实 zip）。 */
    InputStream openArchiveResource(String sourceCodeZipFilename) throws IOException {
        return new ClassPathResource("static/" + sourceCodeZipFilename).getInputStream();
    }

    private static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // 某些文件系统不支持原子 move，退化为普通 move（目标此前不存在，不需要 REPLACE）。
            Files.move(source, target);
        }
    }

    /** 仅当配置了真实（非全零占位）源码哈希时，校验落盘源码包与之逐字节一致。 */
    private void verifyArchiveHash(Path sourceCodePath, String sourceCodeZipFilename) throws IOException {
        String expectedHashHex = nmsciProperties.getSourceCodeZipHash();
        if (!isBoundHash(expectedHashHex)) {
            return;
        }
        String actualHashHex = ByteArrayUtil.bytesToHex(Sha256Util.digest(Files.readAllBytes(sourceCodePath)));
        if (!actualHashHex.equalsIgnoreCase(expectedHashHex)) {
            throw new IllegalStateException("源码包完整性校验失败：" + sourceCodeZipFilename
                    + " 的SHA-256(" + actualHashHex + ")与配置的source-code-zip-hash(" + expectedHashHex + ")不一致");
        }
    }

    /** 全零（或空）哈希为占位符，表示尚未绑定真实源码（构建前/测试），此时不做完整性校验。 */
    private static boolean isBoundHash(String hashHex) {
        return hashHex != null && hashHex.chars().anyMatch(c -> c != '0');
    }
}
