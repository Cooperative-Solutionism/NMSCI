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
import java.util.LinkedHashMap;
import java.util.Map;

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

    private Path datDirectory() {
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
