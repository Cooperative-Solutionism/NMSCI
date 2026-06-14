package com.cooperativesolutionism.nmsci.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上链源码包构建机制的契约测试：保证排除项跨平台一致、关键源文件入包、
 * 且 zip 条目名归一为 '/' 并按序输出（哈希可复现）。
 */
class CalcSourceCodeZipHashContractTest {

    private static final Path ROOT = Path.of("project");

    @Test
    void excludesVcsBuildAndToolingPaths() {
        assertTrue(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve(".git").resolve("config")));
        assertTrue(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve(".idea").resolve("workspace.xml")));
        assertTrue(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve("target").resolve("classes").resolve("App.class")));
        assertTrue(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve("logs").resolve("app.log")));
        assertTrue(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve("temp").resolve("notes.md")));
        assertTrue(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve("nmsci.iml")));
        assertTrue(CalcSourceCodeZipHash.isExcluded(
                ROOT, ROOT.resolve("src").resolve("main").resolve("resources").resolve("static").resolve("source_code_v1.zip")));
    }

    @Test
    void includesKeySourceFiles() {
        assertFalse(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve("pom.xml")));
        assertFalse(CalcSourceCodeZipHash.isExcluded(ROOT, ROOT.resolve("PROTOCOL.md")));
        assertFalse(CalcSourceCodeZipHash.isExcluded(
                ROOT, ROOT.resolve("src").resolve("main").resolve("java")
                        .resolve("com").resolve("cooperativesolutionism").resolve("nmsci").resolve("NmsciApplication.java")));
        assertFalse(CalcSourceCodeZipHash.isExcluded(
                ROOT, ROOT.resolve("src").resolve("main").resolve("resources").resolve("static").resolve("banner.txt")));
        // 路径段仅「包含」排除词但不相等的合法源码不应被排除（按段相等而非子串判定）
        assertFalse(CalcSourceCodeZipHash.isExcluded(
                ROOT, ROOT.resolve("src").resolve("main").resolve("java").resolve("targeting").resolve("X.java")));
        assertFalse(CalcSourceCodeZipHash.isExcluded(
                ROOT, ROOT.resolve("src").resolve("main").resolve("resources").resolve("templates").resolve("x.html")));
    }

    @Test
    void entryNamesUseForwardSlashRegardlessOfPlatform() {
        Path relative = Path.of("src").resolve("main").resolve("java").resolve("App.java");
        assertEquals("src/main/java/App.java", CalcSourceCodeZipHash.toEntryName(relative));
        assertFalse(CalcSourceCodeZipHash.toEntryName(relative).contains("\\"));
    }

    @Test
    void zipExcludesExcludedTreesAndEmitsSortedForwardSlashEntries(@TempDir Path projectDir) throws IOException {
        writeFile(projectDir.resolve("pom.xml"), "<project/>");
        writeFile(projectDir.resolve("PROTOCOL.md"), "protocol");
        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App {}");
        writeFile(projectDir.resolve("src/main/resources/static/banner.txt"), "banner");
        writeFile(projectDir.resolve(".git/HEAD"), "ref");
        writeFile(projectDir.resolve(".idea/workspace.xml"), "x");
        writeFile(projectDir.resolve("target/classes/App.class"), "bytecode");
        writeFile(projectDir.resolve("logs/app.log"), "log");
        writeFile(projectDir.resolve("temp/notes.md"), "notes");
        writeFile(projectDir.resolve("nmsci.iml"), "iml");
        writeFile(projectDir.resolve("src/main/resources/static/source_code_v1.zip"), "oldzip");

        Path zip = projectDir.resolve("target").resolve("out.zip");
        Files.createDirectories(zip.getParent());
        CalcSourceCodeZipHash.zipDirectory(projectDir, zip);

        List<String> entries = zipEntries(zip);

        assertTrue(entries.contains("pom.xml"));
        assertTrue(entries.contains("PROTOCOL.md"));
        assertTrue(entries.contains("src/main/java/com/example/App.java"));
        assertTrue(entries.contains("src/main/resources/static/banner.txt"));

        assertTrue(entries.stream().noneMatch(name -> name.contains(".git/")), ".git must be excluded");
        assertTrue(entries.stream().noneMatch(name -> name.contains(".idea/")), ".idea must be excluded");
        assertTrue(entries.stream().noneMatch(name -> name.startsWith("target/")), "target must be excluded");
        assertTrue(entries.stream().noneMatch(name -> name.contains("logs/")), "logs must be excluded");
        assertTrue(entries.stream().noneMatch(name -> name.contains("temp/")), "temp must be excluded");
        assertTrue(entries.stream().noneMatch(name -> name.endsWith(".iml")), ".iml must be excluded");
        assertTrue(entries.stream().noneMatch(name -> name.contains("static/source_code_v")), "generated source archive must be excluded");

        assertTrue(entries.stream().noneMatch(name -> name.contains("\\")), "entry names must use forward slashes");
        List<String> sorted = new ArrayList<>(entries);
        Collections.sort(sorted);
        assertEquals(sorted, entries, "zip entries must be emitted in sorted order for reproducible hashing");
    }

    @Test
    void zipBytesAreReproducibleAcrossRepeatedRuns(@TempDir Path projectDir) throws IOException {
        writeFile(projectDir.resolve("pom.xml"), "<project/>");
        writeFile(projectDir.resolve("PROTOCOL.md"), "protocol");
        writeFile(projectDir.resolve("src/main/java/com/example/App.java"), "class App {}");

        Path zip1 = projectDir.resolve("target").resolve("a.zip");
        Path zip2 = projectDir.resolve("target").resolve("b.zip");
        Files.createDirectories(zip1.getParent());
        CalcSourceCodeZipHash.zipDirectory(projectDir, zip1);
        CalcSourceCodeZipHash.zipDirectory(projectDir, zip2);

        assertArrayEquals(
                Files.readAllBytes(zip1),
                Files.readAllBytes(zip2),
                "相同源码重复打包应产出字节一致的 zip（固定时间戳 + 排序），否则上链源码哈希不可复现"
        );
    }

    private static void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
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
}
