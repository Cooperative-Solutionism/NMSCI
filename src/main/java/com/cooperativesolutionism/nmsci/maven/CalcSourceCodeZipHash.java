package com.cooperativesolutionism.nmsci.maven;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.stream.Stream;
import java.util.zip.*;
import java.util.*;

public class CalcSourceCodeZipHash {

    /**
     * 需要排除在上链源码包之外的目录段（按相对路径的「路径段」匹配，与平台分隔符无关）。
     */
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", ".idea", "logs", "temp", "target");

    /**
     * 需要排除的生成物标记：static 目录下的历史版本源码包（避免自包含与非确定性）。
     * 以子串匹配——相对路径（'/' 归一）含该标记即排除。
     */
    private static final String EXCLUDED_GENERATED_ARCHIVE_MARKER = "static/source_code_v";

    /**
     * 删除指定目录下的某些文件
     *
     * @param dir   目标目录
     * @param regex 正则表达式，用于匹配要删除的文件
     */
    public static void deleteFiles(Path dir, String regex) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(path -> path.toString().matches(regex))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("Deleted file: " + path);
                        } catch (IOException e) {
                            System.err.println("Failed to delete file: " + path + " - " + e.getMessage());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Traversing directory failed: " + dir + " - " + e.getMessage());
        }
    }

    /**
     * 判断给定文件是否应排除在上链源码包之外。
     * 基于相对路径的「路径段」匹配（目录段命中排除集，或路径含历史源码包前缀，或为 .iml），
     * 全程归一为 '/' 分隔，不依赖运行平台的分隔符，Windows/Linux/macOS 行为一致。
     */
    static boolean isExcluded(Path sourceDir, Path path) {
        String relativePath = toEntryName(sourceDir.relativize(path));
        if (relativePath.isEmpty()) {
            return true;
        }
        for (String segment : relativePath.split("/")) {
            if (EXCLUDED_DIRECTORIES.contains(segment)) {
                return true;
            }
        }
        if (relativePath.contains(EXCLUDED_GENERATED_ARCHIVE_MARKER)) {
            return true;
        }
        return relativePath.endsWith(".iml");
    }

    /**
     * 将相对路径归一为以 '/' 分隔的 zip 条目名，保证上链哈希与运行平台的路径分隔符无关。
     */
    static String toEntryName(Path relativePath) {
        List<String> segments = new ArrayList<>();
        for (Path segment : relativePath) {
            segments.add(segment.toString());
        }
        return String.join("/", segments);
    }

    /**
     * 将指定目录压缩为 ZIP 文件。
     * 条目名归一为 '/' 分隔、按条目名排序、并固定时间戳；配合仓库 .gitattributes 将文本统一为 LF
     * （* text=auto eol=lf），相同源码在任意平台/文件系统上产出字节一致的 zip，故上链源码哈希可复现可信。
     *
     * @param sourceDir   源目录
     * @param zipFilePath 压缩后的 ZIP 文件路径
     * @throws IOException 如果压缩过程中发生错误
     */
    public static void zipDirectory(Path sourceDir, Path zipFilePath) throws IOException {
        List<Path> includedFiles;
        try (Stream<Path> paths = Files.walk(sourceDir)) {
            includedFiles = paths
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> !isExcluded(sourceDir, path))
                    .sorted(Comparator.comparing(path -> toEntryName(sourceDir.relativize(path))))
                    .toList();
        }

        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (Path path : includedFiles) {
                ZipEntry zipEntry = new ZipEntry(toEntryName(sourceDir.relativize(path)));
                zipEntry.setTime(1L); // 固定时间戳，避免每次打包时间不同导致哈希值不同
                zos.putNextEntry(zipEntry);
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * 计算指定文件的 SHA-256 哈希值
     *
     * @param filePath 要计算哈希值的文件路径
     * @return 文件的 SHA-256 哈希值
     * @throws Exception 如果计算哈希值过程中发生错误
     */
    public static String calcFileHash(Path filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(filePath);
             DigestInputStream dis = new DigestInputStream(is, digest)) {
            while (true) {
                if (dis.read() == -1) break;
            }
        }
        byte[] hashBytes = digest.digest();
        return ByteArrayUtil.bytesToHex(hashBytes);
    }

    /**
     * 读取 application.properties 文件
     */
    public static Properties readProperties(Path propertiesFilePath) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(propertiesFilePath)) {
            try (InputStream input = Files.newInputStream(propertiesFilePath)) {
                properties.load(input);
            }
        }
        return properties;
    }

    /**
     * 将哈希值插入或更新 application.properties 文件
     *
     * @param hashValue          要插入的哈希值
     * @param propertiesFilePath properties 文件路径
     * @param propertyKey        要插入或更新的属性键
     * @throws IOException 如果文件操作过程中发生错误
     */
    public static void insertHashToProperties(String hashValue, Path propertiesFilePath, String propertyKey) throws IOException {
        Properties properties = readProperties(propertiesFilePath);

        // 将哈希值插入或更新 properties 文件
        properties.setProperty(propertyKey, hashValue);

        // 保存回 properties 文件
        try (OutputStream output = Files.newOutputStream(propertiesFilePath)) {
            properties.store(output, "Updated source code zip hash");
        }
    }

    public static void main(String[] args) {
        try {
            // 项目根目录
            String rootDir = System.getProperty("user.dir");
            Path sourceDir = Paths.get(rootDir);
            Path propertiesFilePath = Paths.get(rootDir, "target", "classes", "application.properties");

            // 删除已存在的 zip 文件
            Path staticDir = Paths.get(rootDir, "target", "classes", "static");
            deleteFiles(staticDir, ".*source_code_v.*\\.zip");

            Properties properties = readProperties(propertiesFilePath);
            Path zipFilePath = Paths.get(rootDir, "target", "classes", "static", "source_code_v" + properties.getProperty("nmsci.block-version") + ".zip");

            // 压缩文件夹为 zip 文件
            zipDirectory(sourceDir, zipFilePath);
            System.out.println("The file has been compressed to: " + zipFilePath);

            // 计算哈希值
            String hashValue = calcFileHash(zipFilePath);
            System.out.println("Calculated file hash value: " + hashValue);

            // 将哈希值插入 application.properties 文件
            insertHashToProperties(hashValue, propertiesFilePath, "nmsci.source-code-zip-hash");

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }
}
