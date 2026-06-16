# Source Hash Build Tool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden source-code zip/hash generation so the archive file set comes from Git-tracked files and any hash-generation failure fails the build.

**Architecture:** Keep `CalcSourceCodeZipHash` as the build-tool boundary, but replace directory walking and hand-written exclusion rules with `git ls-files -z`. Preserve deterministic zip output by sorting normalized entry names and fixing timestamps. Make Maven and Docker builds compatible with the Git-backed file set by failing antrun on non-zero exit and ensuring Docker build context contains Git metadata.

**Tech Stack:** Java 21, Maven antrun, JUnit 5, Git CLI, Dockerfile/.dockerignore.

---

## Scope Check

This plan implements `docs/superpowers/specs/2026-06-16-source-hash-build-tool-design.md`.

In scope:
- Git-tracked file discovery for source zip generation.
- Current-worktree file content in the zip.
- Fail-fast behavior for Git, zip, hash, cleanup, and properties failures.
- Maven antrun `failonerror` so Java process failures fail the build.
- Docker build compatibility with `git ls-files`.
- Audit status documentation update.

Out of scope:
- Requiring a clean worktree.
- Reading Git `HEAD` blobs instead of files.
- Adding a skip-source-hash flag.
- Runtime semantics for `nmsci.source-code-zip-hash`.
- Concurrent allocation tests, Secp256k1 primitive tests, or broad service/template refactors.

## File Structure

- Modify `src/main/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHash.java`: replace hand-written exclusion traversal with Git-backed file discovery, deterministic zip writing from tracked files, fail-fast `run`, and non-zero `main`.
- Modify `src/test/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHashContractTest.java`: test Git-tracked file selection, current-worktree content, ignored/untracked exclusions, generated archive exclusion, empty repo failure, and `run` writing the hash.
- Modify `pom.xml`: add `failonerror="true"` to the antrun `<java>` task.
- Modify `.dockerignore`: stop excluding `.git` and update comments to explain Git metadata is required by the build tool.
- Modify `Dockerfile`: make Git availability explicit in the build stage and update source-hash comments.
- Modify `docs/code-quality-audit-status.md`: mark source hash hardening fixed and record verification.

---

## Task 1: Add Failing Git-Backed Source Hash Contract Tests

**Files:**
- Modify: `src/test/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHashContractTest.java`

- [ ] **Step 1: Add imports used by the new Git-backed tests**

In `CalcSourceCodeZipHashContractTest.java`, add these imports:

```java
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
```

Keep the existing `ZipInputStream`, `ZipEntry`, and assertion imports.

- [ ] **Step 2: Replace exclusion-only tests with Git file-set contract tests**

Delete the old `excludesVcsBuildAndToolingPaths()` and `includesKeySourceFiles()` methods. Add these tests in their place:

```java
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
```

- [ ] **Step 3: Update deterministic zip tests to use Git-tracked files**

Replace the body of `zipExcludesExcludedTreesAndEmitsSortedForwardSlashEntries(@TempDir Path projectDir)` with this Git-backed version:

```java
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
```

Replace the body of `zipBytesAreReproducibleAcrossRepeatedRuns(@TempDir Path projectDir)` with:

```java
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
```

- [ ] **Step 4: Add a `run` contract test for hash insertion**

Add this test below the reproducibility test:

```java
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
```

- [ ] **Step 5: Add helper methods**

Add these helpers at the bottom of `CalcSourceCodeZipHashContractTest`, above the existing `zipEntries` helper or replacing helper area as needed:

```java
private static void initGitRepo(Path projectDir) throws IOException, InterruptedException {
    Files.createDirectories(projectDir);
    git(projectDir, "init");
    git(projectDir, "config", "user.email", "test@example.local");
    git(projectDir, "config", "user.name", "Test User");
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
```

Keep the existing `writeFile` and `zipEntries` helpers, but ensure `writeFile` can write root-level files:

```java
private static void writeFile(Path file, String content) throws IOException {
    if (file.getParent() != null) {
        Files.createDirectories(file.getParent());
    }
    Files.writeString(file, content);
}
```

- [ ] **Step 6: Run the focused test and verify it fails**

Run:

```powershell
.\mvnw.cmd -Dtest=CalcSourceCodeZipHashContractTest test
```

Expected: FAIL at compile time because `trackedFiles`, `zipTrackedFiles`, `SourceHashResult`, and `run(Path)` do not exist yet.

Do not commit after this red test run.

---

## Task 2: Implement Git-Tracked Source Zip Generation and Fail-Fast Tooling

**Files:**
- Modify: `src/main/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHash.java`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHashContractTest.java`

- [ ] **Step 1: Replace broad imports with explicit build-tool imports**

In `CalcSourceCodeZipHash.java`, replace the wildcard import block with:

```java
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
```

- [ ] **Step 2: Add constants and result record**

Inside `CalcSourceCodeZipHash`, replace `EXCLUDED_DIRECTORIES` and `EXCLUDED_GENERATED_ARCHIVE_MARKER` with:

```java
private static final String SOURCE_HASH_PROPERTY = "nmsci.source-code-zip-hash";
private static final String BLOCK_VERSION_PROPERTY = "nmsci.block-version";
private static final String GENERATED_ARCHIVE_ENTRY_PREFIX = "src/main/resources/static/source_code_v";
private static final String GENERATED_ARCHIVE_ENTRY_SUFFIX = ".zip";

public record SourceHashResult(Path zipFilePath, String hashValue) {
}
```

- [ ] **Step 3: Add Git-backed file discovery**

Add these methods after the constants:

```java
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
    if (!path.isBlank()) {
        files.add(Path.of(path));
    }
}

static boolean shouldIncludeTrackedFile(Path relativePath) {
    String entryName = toEntryName(relativePath);
    return !(entryName.startsWith(GENERATED_ARCHIVE_ENTRY_PREFIX)
            && entryName.endsWith(GENERATED_ARCHIVE_ENTRY_SUFFIX));
}
```

- [ ] **Step 4: Keep entry-name normalization and remove obsolete exclusion API**

Keep `toEntryName(Path relativePath)` as the path normalization helper. Delete `isExcluded(Path sourceDir, Path path)` because file selection now comes from Git plus `shouldIncludeTrackedFile`.

The retained `toEntryName` method should remain:

```java
static String toEntryName(Path relativePath) {
    List<String> segments = new ArrayList<>();
    for (Path segment : relativePath) {
        segments.add(segment.toString());
    }
    return String.join("/", segments);
}
```

- [ ] **Step 5: Make old zip cleanup fail-fast**

Replace `deleteFiles` with:

```java
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
```

- [ ] **Step 6: Add tracked-file zip writing and preserve `zipDirectory` compatibility**

Replace `zipDirectory` with this compatibility wrapper and add `zipTrackedFiles`:

```java
public static void zipDirectory(Path sourceDir, Path zipFilePath) throws IOException {
    zipTrackedFiles(sourceDir, trackedFiles(sourceDir), zipFilePath);
}

public static void zipTrackedFiles(Path root, List<Path> trackedFiles, Path zipFilePath) throws IOException {
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path normalizedZipPath = zipFilePath.toAbsolutePath().normalize();
    if (normalizedZipPath.getParent() != null) {
        Files.createDirectories(normalizedZipPath.getParent());
    }

    List<Path> sortedFiles = trackedFiles.stream()
            .filter(CalcSourceCodeZipHash::shouldIncludeTrackedFile)
            .sorted(Comparator.comparing(CalcSourceCodeZipHash::toEntryName))
            .toList();

    try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(normalizedZipPath)))) {
        for (Path relativePath : sortedFiles) {
            Path source = normalizedRoot.resolve(relativePath).normalize();
            if (!source.startsWith(normalizedRoot)) {
                throw new IOException("tracked path escapes source root: " + relativePath);
            }
            if (!Files.isRegularFile(source)) {
                throw new IOException("tracked file is missing or not a regular file: " + relativePath);
            }

            ZipEntry zipEntry = new ZipEntry(toEntryName(relativePath));
            zipEntry.setTime(1L);
            zos.putNextEntry(zipEntry);
            Files.copy(source, zos);
            zos.closeEntry();
        }
    }
}
```

- [ ] **Step 7: Use buffered hash reads**

Replace `calcFileHash` with:

```java
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
```

- [ ] **Step 8: Add required-properties reading and fail-fast `run`**

Keep `readProperties` and `insertHashToProperties`, then add this method before `main`:

```java
private static Properties readRequiredProperties(Path propertiesFilePath) throws IOException {
    if (!Files.isRegularFile(propertiesFilePath)) {
        throw new IOException("required properties file does not exist: " + propertiesFilePath);
    }
    return readProperties(propertiesFilePath);
}

public static SourceHashResult run(Path root) throws IOException, NoSuchAlgorithmException {
    Path sourceDir = root.toAbsolutePath().normalize();
    Path propertiesFilePath = sourceDir.resolve("target").resolve("classes").resolve("application.properties");
    Properties properties = readRequiredProperties(propertiesFilePath);

    String blockVersion = properties.getProperty(BLOCK_VERSION_PROPERTY);
    if (blockVersion == null || blockVersion.isBlank()) {
        throw new IllegalStateException(BLOCK_VERSION_PROPERTY + " is missing in " + propertiesFilePath);
    }

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
```

- [ ] **Step 9: Make `main` exit non-zero on failure**

Replace `main` with:

```java
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
```

- [ ] **Step 10: Run the focused build-tool tests**

Run:

```powershell
.\mvnw.cmd -Dtest=CalcSourceCodeZipHashContractTest test
```

Expected: PASS. The test count may change from the previous 5 tests, but there must be zero failures and zero errors.

- [ ] **Step 11: Commit build-tool implementation**

Run:

```powershell
git add src/main/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHash.java src/test/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHashContractTest.java
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'fix: 使用Git跟踪文件生成源码哈希'
```

---

## Task 3: Make Maven and Docker Builds Honor the Fail-Fast Contract

**Files:**
- Modify: `pom.xml`
- Modify: `.dockerignore`
- Modify: `Dockerfile`
- Test: `src/test/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHashContractTest.java`

- [ ] **Step 1: Make Maven antrun fail when the Java tool exits non-zero**

In `pom.xml`, change the antrun Java task from:

```xml
<java classname="com.cooperativesolutionism.nmsci.buildtool.CalcSourceCodeZipHash"
      fork="true">
```

to:

```xml
<java classname="com.cooperativesolutionism.nmsci.buildtool.CalcSourceCodeZipHash"
      fork="true"
      failonerror="true">
```

- [ ] **Step 2: Keep `.git` in Docker build context**

In `.dockerignore`, replace the top comment block with:

```text
# Source hash generation uses `git ls-files`, so Docker build context must keep
# `.git` metadata. The source zip itself will not include `.git` because Git does
# not track its own metadata.
#
# Rules here should only remove local/build/runtime files that are not required
# to run `git ls-files` and read tracked working-tree content inside docker build.
```

Remove the `.git` exclude line from `.dockerignore`. Keep the existing excludes for `.idea`, `*.iml`, `target`, `logs`, `temp`, `src/main/resources/static/source_code_*.zip`, `application-local.properties`, and `.env`.

- [ ] **Step 3: Make Git availability explicit in the Docker build stage**

In `Dockerfile`, replace the source-hash reproducibility comment near the top with:

```dockerfile
# Source hash generation runs in Maven prepare-package and uses `git ls-files`.
# The build context therefore includes `.git` metadata, while the generated zip
# still contains only tracked working-tree files, never Git metadata itself.
```

After `WORKDIR /build`, add:

```dockerfile
RUN if ! command -v git >/dev/null 2>&1; then \
        apt-get update && apt-get install -y --no-install-recommends git && rm -rf /var/lib/apt/lists/*; \
    fi
```

Replace the `COPY . .` comment with:

```dockerfile
# Copy the source tree and Git metadata so CalcSourceCodeZipHash can use
# `git ls-files` during prepare-package.
```

- [ ] **Step 4: Run build-tool tests and Maven package**

Run:

```powershell
.\mvnw.cmd -Dtest=CalcSourceCodeZipHashContractTest test
.\mvnw.cmd -DskipTests package
```

Expected:
- `CalcSourceCodeZipHashContractTest` passes.
- `package` reaches `prepare-package`, generates `target/classes/static/source_code_v<version>.zip`, writes a non-zero `nmsci.source-code-zip-hash`, and finishes with `BUILD SUCCESS`.

- [ ] **Step 5: Run Docker build-stage verification**

Run:

```powershell
docker build --target build -t nmsci-source-hash-build-test .
```

Expected: PASS if Docker can build the Maven image and access dependencies. If the build fails because Docker or network access is unavailable, capture the exact failure and do not mark Docker build verified in docs.

- [ ] **Step 6: Commit Maven and Docker build integration**

Run:

```powershell
git add pom.xml .dockerignore Dockerfile
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'build: 让源码哈希失败中止构建'
```

---

## Task 4: Update Audit Status and Run Full Verification

**Files:**
- Modify: `docs/code-quality-audit-status.md`
- Test: full Maven verification

- [ ] **Step 1: Mark source hash hardening as fixed**

In `docs/code-quality-audit-status.md`, move this item from `## 3. 有意延后` / `### 较高价值（建议下一轮优先）`:

```markdown
- **源码哈希排除集漂移 + 吞异常**（Medium，Config）：`buildtool/CalcSourceCodeZipHash.java` 排除集与 .gitignore/.dockerignore 不一致，且失败返回 0 → 可能打包陈旧/全零链上哈希。建议改 `git ls-files` 取文件集 + 失败 `System.exit(1)`。
```

Add this bullet under `### 2.4 本轮新增修复（2026-06-16）`:

```markdown
- ✅ **源码哈希构建工具硬化**：源码 zip 文件集改为 `git ls-files -z` 的已跟踪文件，内容读取当前工作树；历史 `source_code_v*.zip` 显式排除；Git/zip/hash/properties 失败会让工具非 0 退出，Maven antrun 通过 `failonerror=true` 中止构建；Docker build context 保留 `.git` 以支持 `git ls-files`。
```

- [ ] **Step 2: Update next-round priority list**

In `## 7. 下一轮建议优先级`, replace:

```markdown
1. 源码哈希失败兜底 + 排除集对齐（链上完整性）。
2. 双挂载 / 并发分配的真实两线程测试；Secp256k1 原语负路径测试。
3. 其余结构性重构（写 Service 模板化、常量化、去重）按团队节奏推进。
```

with:

```markdown
1. 双挂载 / 并发分配的真实两线程测试；Secp256k1 原语负路径测试。
2. 其余结构性重构（写 Service 模板化、常量化、去重）按团队节奏推进。
```

- [ ] **Step 3: Run focused verification**

Run:

```powershell
.\mvnw.cmd -Dtest=CalcSourceCodeZipHashContractTest test
.\mvnw.cmd -DskipTests package
```

Expected: both commands pass. Record the focused test count and package outcome for the verification section.

- [ ] **Step 4: Run full unit test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: PASS with zero failures and zero errors. Record the final surefire test count from the Maven summary.

- [ ] **Step 5: Run full Maven verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: PASS if Docker/Testcontainers is available. Record the final failsafe integration test count from the Maven summary.

- [ ] **Step 6: Update verification counts in the audit document**

In `docs/code-quality-audit-status.md`, update the verification-count lines to match the exact output from Steps 3-5. The count lines currently mention:

```markdown
targeted surefire（10 tests）
targeted failsafe `verify`（29 tests）
full `mvnw test`（148 tests）
full `mvnw verify`（surefire 148 + failsafe 34 tests）
```

Replace them with the exact counts from this run. Do not guess counts before the commands have completed.

- [ ] **Step 7: Commit docs and verification status**

Run:

```powershell
git add docs/code-quality-audit-status.md
git -c user.name='GPT5.5XH' -c user.email='gpt5.5xh@example.local' commit -m 'docs: 更新源码哈希硬化审计状态'
```

---

## Task 5: Final Review and Completion

**Files:**
- No planned code changes. Fix only issues found by review or verification.

- [ ] **Step 1: Confirm legacy exclusion API is gone from tests and docs are aligned**

Run:

```powershell
rg -n "isExcluded\\(|EXCLUDED_DIRECTORIES|EXCLUDED_GENERATED_ARCHIVE_MARKER|失败返回 0|源码哈希失败兜底 \\+ 排除集对齐" src docs pom.xml Dockerfile .dockerignore
```

Expected:
- No production/test references to `isExcluded(` or old exclusion constants.
- No remaining unresolved audit text claiming source hash hardening is still next priority.
- Historical plan/spec references may appear under `docs/superpowers/...`; if they do, confirm they are old design/plan records, not current audit status.

- [ ] **Step 2: Confirm fail-fast Maven integration**

Run:

```powershell
rg -n "CalcSourceCodeZipHash|failonerror=\"true\"|git ls-files|source_code_v" pom.xml Dockerfile .dockerignore src/main/java/com/cooperativesolutionism/nmsci/buildtool/CalcSourceCodeZipHash.java
```

Expected:
- `pom.xml` has `failonerror="true"` on the antrun `<java>` task.
- Dockerfile comment and build stage acknowledge `git ls-files`.
- `.dockerignore` does not exclude `.git`.
- `CalcSourceCodeZipHash` uses `git -C <root> ls-files -z`.

- [ ] **Step 3: Verify commit authors**

Run:

```powershell
git log --format="%h %an <%ae> %s" -8
```

Expected: new implementation commits use `GPT5.5XH <gpt5.5xh@example.local>` and Chinese commit messages.

- [ ] **Step 4: Final verification**

Run:

```powershell
git status --short
git diff --check
.\mvnw.cmd test
.\mvnw.cmd verify
```

Expected:
- `git status --short` is clean.
- `git diff --check` has no output.
- `test` passes with zero failures and zero errors.
- `verify` passes with zero failures and zero errors.

- [ ] **Step 5: Final report**

Report:

```text
已完成：源码 zip 文件集改为 git ls-files，当前工作树内容入包，历史 source_code_v*.zip 排除，构建工具失败非 0，Maven antrun failonerror，Docker build context 支持 Git 元数据，审计状态已更新。
验证：列出 CalcSourceCodeZipHashContractTest、mvnw package、mvnw test、mvnw verify、docker build（如已验证）的结果。
提交：列出本轮新增提交哈希和中文提交信息。
残余：并发真实两线程测试、Secp256k1 原语负路径测试、结构性重构仍按审计清单后续推进。
```
