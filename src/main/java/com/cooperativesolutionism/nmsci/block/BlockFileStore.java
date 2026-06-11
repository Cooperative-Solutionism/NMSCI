package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Component
public class BlockFileStore {

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
}
