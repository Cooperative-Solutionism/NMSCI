package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public String copyArchiveForVersion(int blockVersion) {
        String sourceCodeZipFilename = BlockConstants.SOURCE_CODE_ZIP_PREFIX + blockVersion + BlockConstants.SOURCE_CODE_ZIP_SUFFIX;
        Path sourceCodePath = applicationRoot
                .resolve(nmsciProperties.getFileRootDir())
                .resolve(nmsciProperties.getFileSourceCodeDir())
                .resolve(sourceCodeZipFilename);

        try {
            if (!Files.exists(sourceCodePath)) {
                ClassPathResource classPathResource = new ClassPathResource("static/" + sourceCodeZipFilename);
                Files.createDirectories(sourceCodePath.getParent());
                try (var inputStream = classPathResource.getInputStream()) {
                    Files.copy(inputStream, sourceCodePath);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy source code zip file", e);
        }

        return sourceCodePath.getFileName().toString();
    }
}
