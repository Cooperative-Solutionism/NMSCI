package com.cooperativesolutionism.nmsci.buildtool;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

public class CalcSourceCodeZipHash {

    private static final String SOURCE_HASH_PROPERTY = "nmsci.source-code-zip-hash";
    private static final String BLOCK_VERSION_PROPERTY = "nmsci.block-version";
    private static final String GENERATED_ARCHIVE_ENTRY_PREFIX = "src/main/resources/static/source_code_v";
    private static final String GENERATED_ARCHIVE_ENTRY_SUFFIX = ".zip";

    public record SourceHashResult(Path zipFilePath, String hashValue) {
    }

    public static List<Path> trackedFiles(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalStateException("source root does not exist: " + normalizedRoot);
        }

        byte[] output;
        try {
            Process process = new ProcessBuilder("git", "-C", normalizedRoot.toString(), "ls-files", "-z")
                    .redirectErrorStream(true)
                    .start();
            output = process.getInputStream().readAllBytes();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String message = new String(output, StandardCharsets.UTF_8).trim();
                throw new IllegalStateException("git ls-files failed with exit code " + exitCode + ": " + message);
            }
        } catch (IOException e) {
            throw new IllegalStateException("git ls-files failed to start", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git ls-files interrupted", e);
        }

        List<Path> files = parseTrackedFiles(output).stream()
                .filter(CalcSourceCodeZipHash::shouldIncludeTrackedFile)
                .sorted(Comparator.comparing(CalcSourceCodeZipHash::toEntryName))
                .toList();
        if (files.isEmpty()) {
            throw new IllegalStateException("git ls-files returned no tracked files under: " + normalizedRoot);
        }
        return files;
    }

    private static List<Path> parseTrackedFiles(byte[] output) {
        List<Path> files = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < output.length; i++) {
            if (output[i] == 0) {
                addTrackedPath(files, output, start, i);
                start = i + 1;
            }
        }
        if (start < output.length) {
            addTrackedPath(files, output, start, output.length);
        }
        return files;
    }

    private static void addTrackedPath(List<Path> files, byte[] output, int start, int end) {
        if (end <= start) {
            return;
        }
        String path = new String(output, start, end - start, StandardCharsets.UTF_8);
        if (!path.isEmpty()) {
            files.add(Path.of(path));
        }
    }

    static boolean shouldIncludeTrackedFile(Path relativePath) {
        String entryName = toEntryName(relativePath);
        return !(entryName.startsWith(GENERATED_ARCHIVE_ENTRY_PREFIX)
                && entryName.endsWith(GENERATED_ARCHIVE_ENTRY_SUFFIX));
    }

    static String toEntryName(Path relativePath) {
        List<String> segments = new ArrayList<>();
        for (Path segment : relativePath) {
            segments.add(segment.toString());
        }
        return String.join("/", segments);
    }

    public static void deleteFiles(Path dir, String regex) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> pathsToDelete = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().matches(regex))
                    .toList();
            for (Path path : pathsToDelete) {
                Files.delete(path);
                System.out.println("Deleted file: " + path);
            }
        }
    }

    public static void zipDirectory(Path sourceDir, Path zipFilePath) throws IOException {
        zipTrackedFiles(sourceDir, trackedFiles(sourceDir), zipFilePath);
    }

    public static void zipTrackedFiles(Path root, List<Path> trackedFiles, Path zipFilePath) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedZipPath = zipFilePath.toAbsolutePath().normalize();
        List<Path> sortedFiles = validatedZipSources(normalizedRoot, trackedFiles);

        if (normalizedZipPath.getParent() != null) {
            Files.createDirectories(normalizedZipPath.getParent());
        }

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(normalizedZipPath)))) {
            for (Path relativePath : sortedFiles) {
                Path source = normalizedRoot.resolve(relativePath).normalize();
                ZipEntry zipEntry = new ZipEntry(toEntryName(relativePath));
                zipEntry.setTime(1L);
                zos.putNextEntry(zipEntry);
                Files.copy(source, zos);
                zos.closeEntry();
            }
        }
    }

    private static List<Path> validatedZipSources(Path normalizedRoot, List<Path> trackedFiles) throws IOException {
        List<Path> sortedFiles = trackedFiles.stream()
                .filter(CalcSourceCodeZipHash::shouldIncludeTrackedFile)
                .sorted(Comparator.comparing(CalcSourceCodeZipHash::toEntryName))
                .toList();

        for (Path relativePath : sortedFiles) {
            Path source = normalizedRoot.resolve(relativePath).normalize();
            validateTrackedSource(normalizedRoot, relativePath, source);
        }
        return sortedFiles;
    }

    private static void validateTrackedSource(Path normalizedRoot, Path relativePath, Path source) throws IOException {
        if (!source.startsWith(normalizedRoot)) {
            throw new IOException("tracked path escapes source root: " + relativePath);
        }
        assertNoSymbolicLinkInPath(normalizedRoot, relativePath);
        if (Files.isSymbolicLink(source)) {
            throw new IOException("tracked file is a symbolic link: " + relativePath);
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("tracked file is missing or not a regular file: " + relativePath);
        }
    }

    private static void assertNoSymbolicLinkInPath(Path normalizedRoot, Path relativePath) throws IOException {
        Path current = normalizedRoot;
        int segmentIndex = 0;
        int fileNameIndex = relativePath.getNameCount() - 1;
        for (Path segment : relativePath) {
            if (segmentIndex == fileNameIndex) {
                return;
            }
            current = current.resolve(segment).normalize();
            if (Files.isSymbolicLink(current)) {
                throw new IOException("tracked path contains symbolic link: " + relativePath);
            }
            segmentIndex++;
        }
    }

    public static String calcFileHash(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(Files.newInputStream(filePath));
             DigestInputStream digestInput = new DigestInputStream(input, digest)) {
            byte[] buffer = new byte[8192];
            while (digestInput.read(buffer) != -1) {
                // DigestInputStream updates the digest as bytes are read.
            }
        }
        return ByteArrayUtil.bytesToHex(digest.digest());
    }

    public static Properties readProperties(Path propertiesFilePath) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(propertiesFilePath)) {
            try (InputStream input = Files.newInputStream(propertiesFilePath)) {
                properties.load(input);
            }
        }
        return properties;
    }

    public static void insertHashToProperties(String hashValue, Path propertiesFilePath, String propertyKey) throws IOException {
        Properties properties = readProperties(propertiesFilePath);
        properties.setProperty(propertyKey, hashValue);
        try (OutputStream output = Files.newOutputStream(propertiesFilePath)) {
            properties.store(output, "Updated source code zip hash");
        }
    }

    private static Properties readRequiredProperties(Path propertiesFilePath) throws IOException {
        if (!Files.isRegularFile(propertiesFilePath)) {
            throw new IOException("required properties file does not exist: " + propertiesFilePath);
        }
        return readProperties(propertiesFilePath);
    }

    private static String validatedBlockVersion(Properties properties, Path propertiesFilePath) {
        String blockVersion = properties.getProperty(BLOCK_VERSION_PROPERTY);
        if (blockVersion == null || blockVersion.isBlank()) {
            throw new IllegalStateException(BLOCK_VERSION_PROPERTY + " is missing in " + propertiesFilePath);
        }
        String trimmed = blockVersion.trim();
        if (!trimmed.matches("[1-9][0-9]*")) {
            throw new IllegalStateException(BLOCK_VERSION_PROPERTY + " must be a positive integer in " + propertiesFilePath);
        }
        return trimmed;
    }

    public static SourceHashResult run(Path root) throws IOException, NoSuchAlgorithmException {
        Path sourceDir = root.toAbsolutePath().normalize();
        Path propertiesFilePath = sourceDir.resolve("target").resolve("classes").resolve("application.properties");
        Properties properties = readRequiredProperties(propertiesFilePath);
        String blockVersion = validatedBlockVersion(properties, propertiesFilePath);

        Path staticDir = sourceDir.resolve("target").resolve("classes").resolve("static");
        Files.createDirectories(staticDir);
        deleteFiles(staticDir, ".*source_code_v.*\\.zip");

        Path zipFilePath = staticDir.resolve("source_code_v" + blockVersion + ".zip");
        zipTrackedFiles(sourceDir, trackedFiles(sourceDir), zipFilePath);
        System.out.println("The file has been compressed to: " + zipFilePath);

        String hashValue = calcFileHash(zipFilePath);
        System.out.println("Calculated file hash value: " + hashValue);

        insertHashToProperties(hashValue, propertiesFilePath, SOURCE_HASH_PROPERTY);
        return new SourceHashResult(zipFilePath, hashValue);
    }

    public static void main(String[] args) {
        try {
            Path root = Paths.get(System.getProperty("user.dir"));
            run(root);
        } catch (Exception e) {
            System.err.println("source code zip hash generation failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
