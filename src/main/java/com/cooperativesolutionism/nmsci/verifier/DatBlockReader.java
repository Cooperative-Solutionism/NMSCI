package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@code blk*.dat} 区块二进制读取器（写入侧只提供序列化，本类补齐反序列化）。
 *
 * <p>一个 .dat 文件是「记录」的裸拼接，无文件头/尾/分隔符；每条记录 =
 * 4 字节大端魔数 {@code 0x6A466D85} + 8 字节大端原始区块长度 N + N 字节原始区块（229 字节头 + 区块体）。
 * 多文件链按 {@code blkNNNNNNNN.dat} 索引升序逻辑拼接，区块高度跨文件连续、不重置。
 */
public final class DatBlockReader {

    private static final int MAGIC_NUMBER = BlockConstants.MAGIC_NUMBER;
    private static final int MAGIC_FIELD_BYTES = Integer.BYTES;
    private static final int LENGTH_FIELD_BYTES = Long.BYTES;
    private static final int RECORD_PREFIX_BYTES = MAGIC_FIELD_BYTES + LENGTH_FIELD_BYTES;

    /** 防止损坏的长度前缀触发超大分配的防御上限（远大于 block-max-size 的 1 MiB）。 */
    private static final long MAX_REASONABLE_RAW_BLOCK_BYTES = 64L * 1024 * 1024;

    private DatBlockReader() {
    }

    /**
     * 按 {@code blkNNNNNNNN.dat} 索引升序读取目录下全部区块；目录不存在或无 .dat 文件时返回空列表。
     */
    public static List<ParsedBlock> readDirectory(Path datDir) {
        if (datDir == null || !Files.isDirectory(datDir)) {
            return List.of();
        }

        List<Path> datFiles = listDatFilesSortedByIndex(datDir);
        List<ParsedBlock> blocks = new ArrayList<>();
        for (Path datFile : datFiles) {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(datFile);
            } catch (IOException e) {
                throw new UncheckedIOException("读取区块文件失败: " + datFile, e);
            }
            readConcatenatedInto(blocks, bytes, datFile.getFileName().toString());
        }
        return blocks;
    }

    /**
     * 解析单个 .dat 字节流（一个或多个连续记录）。
     */
    public static List<ParsedBlock> readConcatenated(byte[] datBytes, String datFileName) {
        List<ParsedBlock> blocks = new ArrayList<>();
        readConcatenatedInto(blocks, datBytes, datFileName);
        return blocks;
    }

    /**
     * 解析单个原始区块（不含 12 字节 .dat 记录前缀）。
     */
    public static ParsedBlock parseRawBlock(byte[] rawBlock) {
        return parseRawBlock(rawBlock, null);
    }

    private static void readConcatenatedInto(List<ParsedBlock> blocks, byte[] datBytes, String datFileName) {
        if (datBytes == null) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.wrap(datBytes);
        int total = datBytes.length;
        int position = 0;
        while (position < total) {
            if (total - position < RECORD_PREFIX_BYTES) {
                throw new BlockFormatException(String.format(
                        "记录前缀不完整 @%d（剩余 %d 字节，至少需要 %d）", position, total - position, RECORD_PREFIX_BYTES));
            }

            int magic = buffer.getInt(position);
            if (magic != MAGIC_NUMBER) {
                throw new BlockFormatException(String.format(
                        "魔数不匹配 @%d：期望 0x%08X，实得 0x%08X", position, MAGIC_NUMBER, magic));
            }

            long rawLength = buffer.getLong(position + MAGIC_FIELD_BYTES);
            if (rawLength < ParsedBlock.HEADER_SIZE) {
                throw new BlockFormatException(String.format(
                        "区块长度 %d 小于区块头 %d @%d", rawLength, ParsedBlock.HEADER_SIZE, position));
            }
            if (rawLength > MAX_REASONABLE_RAW_BLOCK_BYTES) {
                throw new BlockFormatException(String.format(
                        "区块长度 %d 超过合理上限 %d @%d（疑似长度前缀损坏）", rawLength, MAX_REASONABLE_RAW_BLOCK_BYTES, position));
            }

            long recordEnd = (long) position + RECORD_PREFIX_BYTES + rawLength;
            if (recordEnd > total) {
                throw new BlockFormatException(String.format(
                        "区块体超出文件边界 @%d：声明 %d 字节，但仅剩 %d", position, rawLength, total - position - RECORD_PREFIX_BYTES));
            }

            int rawStart = position + RECORD_PREFIX_BYTES;
            byte[] rawBlock = Arrays.copyOfRange(datBytes, rawStart, rawStart + (int) rawLength);
            blocks.add(parseRawBlock(rawBlock, datFileName));
            position = (int) recordEnd;
        }
    }

    private static ParsedBlock parseRawBlock(byte[] rawBlock, String datFileName) {
        if (rawBlock.length < ParsedBlock.HEADER_SIZE) {
            throw new BlockFormatException("原始区块字节数 " + rawBlock.length + " 小于区块头 " + ParsedBlock.HEADER_SIZE);
        }

        ByteBuffer buffer = ByteBuffer.wrap(rawBlock);
        int cursor = ParsedBlock.HEADER_SIZE;
        List<ParsedMessage> messages = new ArrayList<>();

        for (MsgTypeEnum type : MsgTypeEnum.values()) {
            if (rawBlock.length - cursor < BlockConstants.MESSAGE_COUNT_FIELD_SIZE) {
                throw new BlockFormatException("区块体不足以读取 " + type + " 段计数字段 @" + cursor);
            }
            long count = buffer.getLong(cursor);
            cursor += BlockConstants.MESSAGE_COUNT_FIELD_SIZE;

            if (count < 0 || count > rawBlock.length) {
                throw new BlockFormatException(type + " 段消息计数非法: " + count);
            }
            int recordSize = type.getSize();
            long sectionBytes = count * (long) recordSize;
            if (sectionBytes > rawBlock.length - cursor) {
                throw new BlockFormatException(String.format(
                        "%s 段越界：需要 %d 字节，剩余 %d", type, sectionBytes, rawBlock.length - cursor));
            }

            for (long i = 0; i < count; i++) {
                byte[] record = Arrays.copyOfRange(rawBlock, cursor, cursor + recordSize);
                messages.add(new ParsedMessage(type, record));
                cursor += recordSize;
            }
        }

        if (cursor != rawBlock.length) {
            throw new BlockFormatException(String.format(
                    "区块体未在声明长度处恰好结束：解析至 %d，区块长 %d", cursor, rawBlock.length));
        }

        return new ParsedBlock(rawBlock, messages, datFileName);
    }

    private static List<Path> listDatFilesSortedByIndex(Path datDir) {
        try (Stream<Path> paths = Files.list(datDir)) {
            return paths
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> datFileIndex(path.getFileName().toString()) >= 0)
                    .sorted(Comparator.comparingLong(path -> datFileIndex(path.getFileName().toString())))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("枚举区块目录失败: " + datDir, e);
        }
    }

    /** 解析 {@code blkNNNNNNNN.dat} 的数字索引；不符合命名规则返回 -1。 */
    private static long datFileIndex(String fileName) {
        if (!fileName.startsWith(BlockConstants.DAT_FILE_PREFIX) || !fileName.endsWith(BlockConstants.DAT_FILE_SUFFIX)) {
            return -1L;
        }
        String index = fileName.substring(
                BlockConstants.DAT_FILE_PREFIX.length(),
                fileName.length() - BlockConstants.DAT_FILE_SUFFIX.length());
        if (index.isEmpty() || !index.chars().allMatch(Character::isDigit)) {
            return -1L;
        }
        try {
            return Long.parseLong(index);
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
