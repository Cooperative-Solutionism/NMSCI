package com.cooperativesolutionism.nmsci.buildtool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 上链源码包构建机制的契约测试：保证排除项跨平台一致、关键源文件入包、
 * 且 zip 条目名归一为 '/' 并按序输出（哈希可复现）。
 */
class CalcSourceCodeZipHashContractTest {

    @Test
    void zipUsesGitTrackedFilesAndCurrentWorktreeContent(@TempDir Path projectDir) throws Exception {
        initGitRepo(projectDir);
        writeFile(projectDir.resolve(".gitignore"), "ignored.txt\n");
        writeFile(projectDir.resolve("pom.xml"), "<project/>");
        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App { int version = 1; }");
        writeFile(projectDir.resolve("src/main/resources/static/source_code_v1.zip"), "old-source-zip");
        git(projectDir, "add", ".");
        git(projectDir, "commit", "-m", "initial");

        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App { int version = 2; }");
        writeFile(projectDir.resolve("untracked.txt"), "local");
        writeFile(projectDir.resolve("ignored.txt"), "ignored");

        Path zip = projectDir.resolve("target/out.zip");
        CalcSourceCodeZipHash.zipTrackedFiles(projectDir, CalcSourceCodeZipHash.trackedFiles(projectDir), zip);

        List<String> entries = zipEntries(zip);
        assertTrue(entries.contains(".gitignore"));
        assertTrue(entries.contains("pom.xml"));
        assertTrue(entries.contains("src/main/java/com/example/App.java"));
        assertFalse(entries.contains("untracked.txt"));
        assertFalse(entries.contains("ignored.txt"));
        assertFalse(entries.contains("src/main/resources/static/source_code_v1.zip"));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith(".git/")));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith("target/")));
        assertEquals("class App { int version = 2; }", zipEntryContent(zip, "src/main/java/com/example/App.java"));
    }

    @Test
    void trackedFilesFailsWhenGitRepoHasNoTrackedFiles(@TempDir Path projectDir) throws Exception {
        initGitRepo(projectDir);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> CalcSourceCodeZipHash.trackedFiles(projectDir)
        );

        assertTrue(exception.getMessage().contains("git ls-files returned no tracked files"));
    }

    @Test
    void entryNamesUseForwardSlashRegardlessOfPlatform() {
        Path relative = Path.of("src").resolve("main").resolve("java").resolve("App.java");
        assertEquals("src/main/java/App.java", CalcSourceCodeZipHash.toEntryName(relative));
        assertFalse(CalcSourceCodeZipHash.toEntryName(relative).contains("\\"));
    }

    @Test
    void zipExcludesExcludedTreesAndEmitsSortedForwardSlashEntries(@TempDir Path projectDir) throws Exception {
        initGitRepo(projectDir);
        writeFile(projectDir.resolve(".gitignore"), "ignored.txt\n");
        writeFile(projectDir.resolve("pom.xml"), "<project/>");
        writeFile(projectDir.resolve("PROTOCOL.md"), "protocol");
        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App {}");
        writeFile(projectDir.resolve("src/main/resources/static/banner.txt"), "banner");
        writeFile(projectDir.resolve("src/main/resources/static/source_code_v1.zip"), "oldzip");
        git(projectDir, "add", ".");
        git(projectDir, "commit", "-m", "initial");
        writeFile(projectDir.resolve("ignored.txt"), "ignored");
        writeFile(projectDir.resolve("untracked.txt"), "untracked");

        Path zip = projectDir.resolve("target").resolve("out.zip");
        CalcSourceCodeZipHash.zipTrackedFiles(projectDir, CalcSourceCodeZipHash.trackedFiles(projectDir), zip);

        List<String> entries = zipEntries(zip);

        assertTrue(entries.contains("pom.xml"));
        assertTrue(entries.contains("PROTOCOL.md"));
        assertTrue(entries.contains("src/main/java/com/example/App.java"));
        assertTrue(entries.contains("src/main/resources/static/banner.txt"));

        assertFalse(entries.contains("ignored.txt"));
        assertFalse(entries.contains("untracked.txt"));
        assertTrue(entries.stream().noneMatch(name -> name.startsWith(".git/")), ".git must not be zipped");
        assertTrue(entries.stream().noneMatch(name -> name.startsWith("target/")), "target must not be zipped");
        assertTrue(entries.stream().noneMatch(name -> name.contains("static/source_code_v")), "generated source archive must be excluded");

        assertTrue(entries.stream().noneMatch(name -> name.contains("\\")), "entry names must use forward slashes");
        List<String> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        assertEquals(sorted, entries, "zip entries must be emitted in sorted order for reproducible hashing");
    }

    @Test
    void zipBytesAreReproducibleAcrossRepeatedRuns(@TempDir Path projectDir) throws Exception {
        initGitRepo(projectDir);
        writeFile(projectDir.resolve("pom.xml"), "<project/>");
        writeFile(projectDir.resolve("PROTOCOL.md"), "protocol");
        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App {}");
        git(projectDir, "add", ".");
        git(projectDir, "commit", "-m", "initial");

        Path zip1 = projectDir.resolve("target").resolve("a.zip");
        Path zip2 = projectDir.resolve("target").resolve("b.zip");
        List<Path> files = CalcSourceCodeZipHash.trackedFiles(projectDir);
        CalcSourceCodeZipHash.zipTrackedFiles(projectDir, files, zip1);
        CalcSourceCodeZipHash.zipTrackedFiles(projectDir, files, zip2);

        assertArrayEquals(
                Files.readAllBytes(zip1),
                Files.readAllBytes(zip2),
                "相同源码重复打包应产出字节一致的 zip（固定时间戳 + 排序），否则上链源码哈希不可复现"
        );
    }

    @Test
    void zipRejectsTrackedSymbolicLinksBeforeCopying(@TempDir Path projectDir, @TempDir Path externalDir) throws Exception {
        assumeTrue(canCreateSymlink(projectDir), "symbolic links are not supported or permitted");

        initGitRepo(projectDir);
        git(projectDir, "config", "core.symlinks", "true");
        Path externalFile = externalDir.resolve("secret.txt");
        writeFile(externalFile, "outside repo");
        Path link = projectDir.resolve("src/main/java/com/example/Leaked.txt");
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, externalFile);
        git(projectDir, "add", ".");
        git(projectDir, "commit", "-m", "symlink");

        IOException exception = assertThrows(
                IOException.class,
                () -> CalcSourceCodeZipHash.zipTrackedFiles(projectDir, CalcSourceCodeZipHash.trackedFiles(projectDir), projectDir.resolve("target/out.zip"))
        );

        assertTrue(exception.getMessage().contains("symbolic link"));
    }

    @Test
    void runGeneratesSourceZipAndWritesHashIntoTargetProperties(@TempDir Path projectDir) throws Exception {
        initGitRepo(projectDir);
        writeFile(projectDir.resolve("pom.xml"), "<project/>");
        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App {}");
        git(projectDir, "add", ".");
        git(projectDir, "commit", "-m", "initial");

        Path propertiesFile = projectDir.resolve("target/classes/application.properties");
        writeFile(propertiesFile, """
                nmsci.block-version=7
                nmsci.source-code-zip-hash=0000000000000000000000000000000000000000000000000000000000000000
                """);

        CalcSourceCodeZipHash.SourceHashResult result = CalcSourceCodeZipHash.run(projectDir);

        Path expectedZip = projectDir.resolve("target/classes/static/source_code_v7.zip");
        assertEquals(expectedZip, result.zipFilePath());
        assertTrue(Files.exists(expectedZip));
        assertEquals(CalcSourceCodeZipHash.calcFileHash(expectedZip), result.hashValue());
        assertEquals(64, result.hashValue().length());
        assertNotEquals("0000000000000000000000000000000000000000000000000000000000000000", result.hashValue());

        Properties properties = new Properties();
        try (var input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }
        assertEquals(result.hashValue(), properties.getProperty("nmsci.source-code-zip-hash"));
    }

    @Test
    void runRejectsMalformedBlockVersionsBeforeCreatingZip(@TempDir Path tempDir) throws Exception {
        List<String> malformedVersions = List.of("../7", "7/evil");
        for (int i = 0; i < malformedVersions.size(); i++) {
            Path projectDir = tempDir.resolve("case-" + i);
            initGitRepo(projectDir);
            writeFile(projectDir.resolve("pom.xml"), "<project/>");
            writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App {}");
            git(projectDir, "add", ".");
            git(projectDir, "commit", "-m", "initial");
            Path propertiesFile = projectDir.resolve("target/classes/application.properties");
            writeFile(propertiesFile, "nmsci.block-version=" + malformedVersions.get(i) + "\n");

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> CalcSourceCodeZipHash.run(projectDir)
            );

            assertTrue(exception.getMessage().contains("must be a positive integer"));
            assertTrue(zipFilesUnder(projectDir.resolve("target/classes")).isEmpty());
        }
    }

    private static void writeFile(Path file, String content) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content);
    }

    private static boolean canCreateSymlink(Path projectDir) throws IOException {
        Files.createDirectories(projectDir);
        Path target = Files.createTempFile("nmsci-symlink-target", ".txt");
        Path link = projectDir.resolve("symlink-check");
        try {
            Files.createSymbolicLink(link, target);
            return Files.isSymbolicLink(link);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            return false;
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
        }
    }

    private static void initGitRepo(Path projectDir) throws IOException, InterruptedException {
        Files.createDirectories(projectDir);
        git(projectDir, "init");
        git(projectDir, "config", "user.email", "test@example.local");
        git(projectDir, "config", "user.name", "Test User");
        git(projectDir, "config", "commit.gpgsign", "false");
        git(projectDir, "config", "core.hooksPath", ".git/hooks");
    }

    private static void git(Path projectDir, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(command)
                .directory(projectDir.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, () -> "git " + String.join(" ", args) + " failed\n" + output);
    }

    private static String zipEntryContent(Path zip, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("zip entry not found: " + entryName);
    }

    private static List<String> zipEntries(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    private static List<Path> zipFilesUnder(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".zip"))
                    .toList();
        }
    }
}
