package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class BlockFileStore {

    private static final Object ROLLBACK_RESOURCE_KEY = new Object();

    private final NmsciProperties nmsciProperties;
    private final Path applicationRoot;

    @Autowired
    public BlockFileStore(NmsciProperties nmsciProperties) {
        this(nmsciProperties, Path.of(System.getProperty("user.dir")));
    }

    BlockFileStore(NmsciProperties nmsciProperties, Path applicationRoot) {
        this.nmsciProperties = nmsciProperties;
        this.applicationRoot = applicationRoot;
    }

    public String appendBlock(String previousDatFilepath, byte[] blockBytes) {
        try {
            Path datFilepath = resolveCurrentDatFilepath(previousDatFilepath);
            Files.createDirectories(datFilepath.getParent());
            datFilepath = rotateIfNeeded(datFilepath, blockBytes.length);
            Files.createDirectories(datFilepath.getParent());
            boolean existedBeforeAppend = Files.exists(datFilepath);
            long originalSize = existedBeforeAppend ? Files.size(datFilepath) : 0L;
            registerRollback(datFilepath, originalSize, existedBeforeAppend);

            try (OutputStream outputStream = Files.newOutputStream(
                    datFilepath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                outputStream.write(blockBytes);
            }

            return datFilepath.getFileName().toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 汇总 .dat 存储目录的利用情况：文件数、当前（最高索引）文件名与大小、总字节数、单文件上限。
     * 枚举的是实际写入目录 {@link #datDirectory()}（绝对路径），但对外只报告配置的相对目录
     * {@code <file-root-dir>/<file-dat-dir>}，避免泄露服务器运行目录的绝对路径。只读枚举，不修改任何文件。
     */
    public DatStorageInfo datStorageInfo() {
        Path dir = datDirectory();
        String reportedDir = Path.of(nmsciProperties.getFileRootDir(), nmsciProperties.getFileDatDir()).toString();
        long maxSizePerFile = nmsciProperties.getBlockDatMaxSize();
        if (!Files.exists(dir)) {
            return new DatStorageInfo(reportedDir, 0, null, 0L, 0L, maxSizePerFile);
        }

        try (Stream<Path> paths = Files.list(dir)) {
            List<Path> datFiles = paths
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(BlockConstants.DAT_FILE_PREFIX) && name.endsWith(BlockConstants.DAT_FILE_SUFFIX);
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();

            long totalBytes = 0L;
            for (Path datFile : datFiles) {
                totalBytes += Files.size(datFile);
            }

            Path current = datFiles.isEmpty() ? null : datFiles.get(datFiles.size() - 1);
            String currentFileName = current == null ? null : current.getFileName().toString();
            long currentFileSizeBytes = current == null ? 0L : Files.size(current);

            return new DatStorageInfo(reportedDir, datFiles.size(), currentFileName, currentFileSizeBytes, totalBytes, maxSizePerFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public record DatStorageInfo(
            String datDirectory,
            int fileCount,
            String currentFileName,
            long currentFileSizeBytes,
            long totalBytes,
            long maxSizePerFileBytes
    ) {
    }

    private void registerRollback(Path datFilepath, long originalSize, boolean existedBeforeAppend) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        Path normalizedPath = datFilepath.toAbsolutePath().normalize();
        @SuppressWarnings("unchecked")
        Map<Path, RollbackFileState> rollbackStates =
                (Map<Path, RollbackFileState>) TransactionSynchronizationManager.getResource(ROLLBACK_RESOURCE_KEY);
        if (rollbackStates == null) {
            rollbackStates = new LinkedHashMap<>();
            TransactionSynchronizationManager.bindResource(ROLLBACK_RESOURCE_KEY, rollbackStates);
            Map<Path, RollbackFileState> statesForSynchronization = rollbackStates;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        if (status == STATUS_ROLLED_BACK) {
                            restoreFiles(statesForSynchronization);
                        }
                    } finally {
                        if (TransactionSynchronizationManager.hasResource(ROLLBACK_RESOURCE_KEY)) {
                            TransactionSynchronizationManager.unbindResource(ROLLBACK_RESOURCE_KEY);
                        }
                    }
                }
            });
        }

        rollbackStates.putIfAbsent(
                normalizedPath,
                new RollbackFileState(normalizedPath, originalSize, existedBeforeAppend)
        );
    }

    private void restoreFiles(Map<Path, RollbackFileState> rollbackStates) {
        for (RollbackFileState rollbackState : rollbackStates.values()) {
            try {
                restoreFile(rollbackState);
            } catch (IOException e) {
                throw new RuntimeException("Failed to restore block dat file after transaction rollback", e);
            }
        }
    }

    private void restoreFile(RollbackFileState rollbackState) throws IOException {
        if (!rollbackState.existedBeforeAppend()) {
            Files.deleteIfExists(rollbackState.path());
            return;
        }

        if (!Files.exists(rollbackState.path())) {
            return;
        }

        try (FileChannel fileChannel = FileChannel.open(rollbackState.path(), StandardOpenOption.WRITE)) {
            fileChannel.truncate(rollbackState.originalSize());
        }
    }

    private Path resolveCurrentDatFilepath(String previousDatFilepath) {
        if (previousDatFilepath == null || previousDatFilepath.isBlank()) {
            return datDirectory().resolve(datFilename(0));
        }

        Path previousPath = Paths.get(previousDatFilepath);
        if (previousPath.isAbsolute()) {
            return previousPath;
        }
        if (previousPath.getNameCount() == 1) {
            return datDirectory().resolve(previousPath);
        }
        if (previousPath.startsWith(nmsciProperties.getFileRootDir())) {
            return applicationRoot.resolve(previousPath);
        }
        if (previousPath.startsWith(nmsciProperties.getFileDatDir())) {
            return applicationRoot.resolve(nmsciProperties.getFileRootDir()).resolve(previousPath);
        }
        return datDirectory().resolve(previousPath.getFileName());
    }

    private Path rotateIfNeeded(Path datFilepath, int blockLength) throws IOException {
        if (!Files.exists(datFilepath) || Files.size(datFilepath) + blockLength <= nmsciProperties.getBlockDatMaxSize()) {
            return datFilepath;
        }

        int index = datFileIndex(datFilepath.getFileName().toString());
        return datFilepath.getParent().resolve(datFilename(index + 1));
    }

    /** .dat 写入目录的绝对路径（{@code <user.dir>/<file-root-dir>/<file-dat-dir>}）；启动对账等需枚举该目录的协作者复用。 */
    public Path datDirectory() {
        return applicationRoot.resolve(nmsciProperties.getFileRootDir()).resolve(nmsciProperties.getFileDatDir());
    }

    private String datFilename(int index) {
        return BlockConstants.DAT_FILE_PREFIX
                + String.format("%0" + BlockConstants.DAT_FILE_INDEX_WIDTH + "d", index)
                + BlockConstants.DAT_FILE_SUFFIX;
    }

    private int datFileIndex(String filename) {
        String indexStr = filename.replace(BlockConstants.DAT_FILE_PREFIX, "").replace(BlockConstants.DAT_FILE_SUFFIX, "");
        return Integer.parseInt(indexStr);
    }

    private record RollbackFileState(Path path, long originalSize, boolean existedBeforeAppend) {
    }
}
