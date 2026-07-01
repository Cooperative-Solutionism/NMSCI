package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.verifier.DatBlockReader;
import com.cooperativesolutionism.nmsci.verifier.ParsedBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockFileReconcilerTest {

    private static final String DAT_FILE = "blk00000000.dat";

    @TempDir
    Path datDir;

    private BlockInfoRepository blockInfoRepository;
    private NmsciMetrics nmsciMetrics;
    private BlockFileReconciler reconciler;
    private final Set<String> dbBlockIdHexes = new HashSet<>();

    @BeforeEach
    void setUp() {
        blockInfoRepository = mock(BlockInfoRepository.class);
        nmsciMetrics = mock(NmsciMetrics.class);
        BlockFileStore blockFileStore = mock(BlockFileStore.class);
        when(blockFileStore.datDirectory()).thenReturn(datDir);
        when(blockInfoRepository.existsById(any())).thenAnswer(invocation -> {
            byte[] id = invocation.getArgument(0);
            return dbBlockIdHexes.contains(toHex(id));
        });
        when(blockInfoRepository.existsByIdAndDatFilepath(any(), eq(DAT_FILE))).thenAnswer(invocation -> {
            byte[] id = invocation.getArgument(0);
            return dbBlockIdHexes.contains(toHex(id));
        });
        reconciler = new BlockFileReconciler(blockInfoRepository, blockFileStore, nmsciMetrics);
    }

    @Test
    void truncatesTrailingOrphanFrame() throws IOException {
        byte[] block1 = rawBlock((byte) 0x11);
        byte[] block2 = rawBlock((byte) 0x22);
        byte[] orphan = rawBlock((byte) 0x99);
        markInDb(block1, block2);

        long expected = frameSize(block1) + frameSize(block2);
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE)).thenReturn(expected);
        writeDat(frame(block1), frame(block2), frame(orphan));

        reconciler.reconcileOnStartup();

        Path file = datDir.resolve(DAT_FILE);
        assertEquals(expected, Files.size(file));
        // 截断后剩余字节正好解析为两个库内区块，孤儿块消失
        List<ParsedBlock> remaining = DatBlockReader.readConcatenated(Files.readAllBytes(file), DAT_FILE);
        assertEquals(2, remaining.size());
        verify(nmsciMetrics, times(1)).recordBlockReconcileHealed();
        verify(nmsciMetrics, never()).recordBlockReconcileAnomaly();
    }

    @Test
    void leavesConsistentFileUntouched() throws IOException {
        byte[] block1 = rawBlock((byte) 0x11);
        byte[] block2 = rawBlock((byte) 0x22);
        markInDb(block1, block2);

        long expected = frameSize(block1) + frameSize(block2);
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE)).thenReturn(expected);
        long actual = writeDat(frame(block1), frame(block2));

        reconciler.reconcileOnStartup();

        assertEquals(actual, Files.size(datDir.resolve(DAT_FILE)));
        verify(nmsciMetrics, never()).recordBlockReconcileHealed();
        verify(nmsciMetrics, never()).recordBlockReconcileAnomaly();
    }

    @Test
    void truncatesTornTailBytes() throws IOException {
        byte[] block1 = rawBlock((byte) 0x11);
        markInDb(block1);

        long expected = frameSize(block1);
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE)).thenReturn(expected);
        // 撕裂写：完整区块后跟若干无法构成完整记录前缀的残字节
        writeDat(frame(block1), new byte[]{0x6A, 0x46, 0x6D});

        reconciler.reconcileOnStartup();

        assertEquals(expected, Files.size(datDir.resolve(DAT_FILE)));
        verify(nmsciMetrics, times(1)).recordBlockReconcileHealed();
        verify(nmsciMetrics, never()).recordBlockReconcileAnomaly();
    }

    @Test
    void alarmsButDoesNotTouchBuriedMidStreamOrphan() throws IOException {
        byte[] block1 = rawBlock((byte) 0x11);
        byte[] orphan = rawBlock((byte) 0x99);
        byte[] block2 = rawBlock((byte) 0x22);
        markInDb(block1, block2);

        long expected = frameSize(block1) + frameSize(block2);
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE)).thenReturn(expected);
        long actual = writeDat(frame(block1), frame(orphan), frame(block2));

        reconciler.reconcileOnStartup();

        // 孤儿夹在中段：截断会误删库内区块，故只告警不动手，文件保持原样
        assertEquals(actual, Files.size(datDir.resolve(DAT_FILE)));
        verify(nmsciMetrics, never()).recordBlockReconcileHealed();
        verify(nmsciMetrics, times(1)).recordBlockReconcileAnomaly();
    }

    @Test
    void deletesFullyOrphanFileWithNoDbBlocks() throws IOException {
        byte[] orphan = rawBlock((byte) 0x99);
        // dbBlockIdHexes 为空：库内无任何区块（如创世出块写盘后未提交即崩溃）
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE)).thenReturn(0L);
        writeDat(frame(orphan));

        reconciler.reconcileOnStartup();

        assertFalse(Files.exists(datDir.resolve(DAT_FILE)));
        verify(nmsciMetrics, times(1)).recordBlockReconcileHealed();
        verify(nmsciMetrics, never()).recordBlockReconcileAnomaly();
    }

    @Test
    void alarmsWhenFileShorterThanDbExpects() throws IOException {
        byte[] block1 = rawBlock((byte) 0x11);
        markInDb(block1);
        // 库期望两块的字节，但盘上只有一块 -> 已提交数据缺失，告警不动手
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE))
                .thenReturn(frameSize(block1) + frameSize(rawBlock((byte) 0x22)));
        long actual = writeDat(frame(block1));

        reconciler.reconcileOnStartup();

        assertEquals(actual, Files.size(datDir.resolve(DAT_FILE)));
        verify(nmsciMetrics, never()).recordBlockReconcileHealed();
        verify(nmsciMetrics, times(1)).recordBlockReconcileAnomaly();
    }

    @Test
    void keepsOrphanFileWithUnparseableTailInsteadOfDeleting() throws IOException {
        byte[] orphan = rawBlock((byte) 0x99);
        // expectedSize==0（库无记录），但文件尾部有无法解析的脏区：无法确认其后是否藏库内区块，保守不删、只告警
        when(blockInfoRepository.sumDatFrameBytesByDatFilepath(DAT_FILE)).thenReturn(0L);
        long actual = writeDat(frame(orphan), new byte[]{0x6A, 0x46, 0x6D});

        reconciler.reconcileOnStartup();

        assertEquals(actual, Files.size(datDir.resolve(DAT_FILE)));
        verify(nmsciMetrics, never()).recordBlockReconcileHealed();
        verify(nmsciMetrics, times(1)).recordBlockReconcileAnomaly();
    }

    // --- helpers ---

    private void markInDb(byte[]... rawBlocks) {
        for (byte[] raw : rawBlocks) {
            dbBlockIdHexes.add(toHex(blockId(raw)));
        }
    }

    private long writeDat(byte[]... parts) throws IOException {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(total);
        for (byte[] part : parts) {
            buffer.put(part);
        }
        Files.write(datDir.resolve(DAT_FILE), buffer.array());
        return total;
    }

    private static byte[] rawBlock(byte headerMarker) {
        // 头部 229 字节填入可区分标记；其后每种消息类型一个计数字段默认 0（空块），DatBlockReader 可干净解析。
        byte[] raw = new byte[ParsedBlock.HEADER_SIZE + MsgTypeEnum.values().length * Long.BYTES];
        for (int i = 0; i < ParsedBlock.HEADER_SIZE; i++) {
            raw[i] = headerMarker;
        }
        return raw;
    }

    private static byte[] frame(byte[] rawBlock) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES + rawBlock.length);
        buffer.putInt(BlockConstants.MAGIC_NUMBER);
        buffer.putLong(rawBlock.length);
        buffer.put(rawBlock);
        return buffer.array();
    }

    private static long frameSize(byte[] rawBlock) {
        return (long) Integer.BYTES + Long.BYTES + rawBlock.length;
    }

    private static byte[] blockId(byte[] rawBlock) {
        return DatBlockReader.parseRawBlock(rawBlock).blockId();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
