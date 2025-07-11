package com.cooperativesolutionism.nmsci.maven;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.*;
import java.util.*;

public class CalcSourceCodeZipHash {

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
     * 将指定目录压缩为 ZIP 文件
     *
     * @param sourceDir   源目录
     * @param zipFilePath 压缩后的 ZIP 文件路径
     * @param excludedDir 需要排除的目录列表
     * @throws IOException 如果压缩过程中发生错误
     */
    public static void zipDirectory(Path sourceDir, Path zipFilePath, String[] excludedDir) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath.toFile());
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            try (Stream<Path> paths = Files.walk(sourceDir)) {
                paths.forEach(path -> {
                    try {
                        AtomicBoolean isExcluded = new AtomicBoolean(false);
                        for (String dir : excludedDir) {
                            if (path.toString().contains(dir)) {
                                isExcluded.set(true);
                                break;
                            }
                        }

                        if (!Files.isDirectory(path) && !isExcluded.get()) {
                            ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString());
                            zipEntry.setTime(1L); // 固定时间戳，避免每次打包时间不同导致哈希值不同
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        }
                    } catch (IOException e) {
                        System.err.println("Error while zipping file: " + path + " - " + e.getMessage());
                    }
                });
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
        return bytesToHex(hashBytes);
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 要转换的字节数组
     * @return 十六进制字符串表示
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
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
            Path zipFilePath = Paths.get(rootDir, "target", "classes", "static", "source_code_v" + properties.getProperty("block-version") + ".zip");

            // 压缩文件夹为 zip 文件
            String[] excludedDirs = new String[]{
                    "\\.git\\",
                    "\\.idea\\",
                    "\\logs\\",
                    "\\temp\\",
                    "\\target\\"
            };
            zipDirectory(sourceDir, zipFilePath, excludedDirs);
            System.out.println("The file has been compressed to: " + zipFilePath);

            // 计算哈希值
            String hashValue = calcFileHash(zipFilePath);
            System.out.println("Calculated file hash value: " + hashValue);

            // 将哈希值插入 application.properties 文件
            insertHashToProperties(hashValue, propertiesFilePath, "source-code-zip-hash");

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }
}
