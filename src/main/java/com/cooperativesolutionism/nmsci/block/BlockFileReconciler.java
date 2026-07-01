package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.verifier.DatBlockReader;
import com.cooperativesolutionism.nmsci.verifier.ParsedBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 启动期 .dat 与数据库对账（崩溃恢复）。
 *
 * <p>出块时文件追加（{@link BlockFileStore#appendBlock}）发生在数据库事务提交「之前」，故进程崩溃只会留下
 * 一种不一致：.dat 末尾多出一个「孤儿/撕裂块」——字节已落盘，但对应区块行因事务未提交而不存在于库。
 * 数据库是链位置的唯一真相源（出块按 {@code findTopByOrderByHeightDesc} 续链），孤儿块不污染库、消息不丢
 * （下轮自动重选入块），但会让 {@code GET /verify/chain} 因 .dat 出现库外区块而报失败，且需人工清理。
 *
 * <p>本对账只做「安全自愈」：以库记录的「每文件应有字节数」为准，仅截除库外的「尾部」多余字节，或删除完全
 * 无库记录的整份孤儿文件。<b>绝不删除任何库已记录的区块字节</b>：一旦发现无法安全处理的偏离（文件短于库
 * 期望、孤儿块夹在中段而非尾部、或存在无法解析的脏区），只告警（日志 + 指标）不动手，交由运维核查。
 *
 * <p>由 {@code BlockChainService} 在任一出块路径（定时任务，或中心公钥冻结的同步出块）首次 appendBlock 之前、
 * 且持有 {@code BlockGenerationLock} 时调用一次，故严格早于本进程任何 appendBlock（此时孤儿一定在尾部），
 * 并与所有 appendBlock 跨实例串行（对账期间无并发写）。<b>假设单写者独占该 .dat 目录</b>（生产部署共用同一
 * 数据库，出块已由 pg_advisory 锁串行化）。本类吞掉自身异常，绝不阻断启动/出块。
 */
@Component
public class BlockFileReconciler {

    private static final Logger logger = LoggerFactory.getLogger(BlockFileReconciler.class);

    /** .dat 记录前缀：4 字节魔数 + 8 字节区块长度。 */
    private static final int RECORD_PREFIX_BYTES = Integer.BYTES + Long.BYTES;
    /** 防止损坏的长度前缀触发超大分配的防御上限（与 {@link DatBlockReader} 一致）。 */
    private static final long MAX_REASONABLE_RAW_BLOCK_BYTES = 64L * 1024 * 1024;

    private final BlockInfoRepository blockInfoRepository;
    private final BlockFileStore blockFileStore;
    private final NmsciMetrics nmsciMetrics;

    public BlockFileReconciler(
            BlockInfoRepository blockInfoRepository,
            BlockFileStore blockFileStore,
            NmsciMetrics nmsciMetrics
    ) {
        this.blockInfoRepository = blockInfoRepository;
        this.blockFileStore = blockFileStore;
        this.nmsciMetrics = nmsciMetrics;
    }

    /** 启动期对账入口；吞掉自身异常，绝不阻断启动/出块。 */
    public void reconcileOnStartup() {
        try {
            Path datDir = blockFileStore.datDirectory();
            if (datDir == null || !Files.isDirectory(datDir)) {
                return;
            }
            for (Path datFile : listDatFilesSortedByName(datDir)) {
                reconcileFile(datFile);
            }
        } catch (Exception e) {
            logger.error("启动对账失败（不阻断出块）", e);
        }
    }

    private void reconcileFile(Path datFile) {
        String filename = datFile.getFileName().toString();
        try {
            long actualSize = Files.size(datFile);
            long expectedSize = blockInfoRepository.sumDatFrameBytesByDatFilepath(filename);

            if (actualSize == expectedSize) {
                return; // 一致，常态：直接跳过，不读文件
            }
            if (actualSize < expectedSize) {
                logger.error("启动对账异常：{} 实际 {} 字节 < 数据库期望 {} 字节，疑似已提交区块字节缺失；不自动处理，请人工核查（GET /verify/chain）",
                        filename, actualSize, expectedSize);
                nmsciMetrics.recordBlockReconcileAnomaly();
                return;
            }

            // actualSize > expectedSize：存在数据库之外的多余字节，需进一步判断能否安全截除。
            byte[] bytes = Files.readAllBytes(datFile);
            if (expectedSize == 0) {
                // 该文件无任何数据库记录的区块；仅当复核确认其中不含任何已入库区块时，才删除整份孤儿文件
                // （如中心公钥轮换/创世出块时，新建 .dat、写入后未提交即崩溃的残留）。
                if (!isSafeToDeleteEntireOrphanFile(bytes)) {
                    logger.error("启动对账异常：{} 无对应数据库记录，但含已入库区块或存在无法解析的脏区，放弃删除，请人工核查", filename);
                    nmsciMetrics.recordBlockReconcileAnomaly();
                    return;
                }
                Files.delete(datFile);
                logger.warn("启动对账：删除整份孤儿 .dat 文件 {}（{} 字节，无任何数据库记录的区块）", filename, actualSize);
                nmsciMetrics.recordBlockReconcileHealed();
                return;
            }

            // expectedSize > 0：复核「文件前缀恰为该文件全部数据库区块」后，仅截除尾部多余字节。
            // 若连续库内前缀短于期望，说明孤儿块夹在中段或数据错位，截断会误删库内区块，故只告警不动手。
            long contiguousDbPrefixEnd = scanContiguousDbBackedPrefixEnd(bytes, filename);
            if (contiguousDbPrefixEnd != expectedSize) {
                logger.error("启动对账异常：{} 连续库内区块前缀仅占 {} 字节，与数据库期望 {} 字节不符（疑似孤儿块夹在中段/数据错位），不自动截断，请人工核查",
                        filename, contiguousDbPrefixEnd, expectedSize);
                nmsciMetrics.recordBlockReconcileAnomaly();
                return;
            }
            truncate(datFile, expectedSize);
            logger.warn("启动对账：截除 {} 尾部 {} 字节的孤儿/撕裂数据（{} -> {} 字节，回到数据库记录的链尾）",
                    filename, actualSize - expectedSize, actualSize, expectedSize);
            nmsciMetrics.recordBlockReconcileHealed();
        } catch (Exception e) {
            logger.error("启动对账单文件失败（跳过该文件）: {}", filename, e);
            nmsciMetrics.recordBlockReconcileAnomaly();
        }
    }

    /**
     * 从偏移 0 起按帧前进，返回「连续且属于本文件名(id 与 dat_filepath 双等)的库内帧」覆盖到的末尾偏移；
     * 遇首个非本文件库内帧或不可解析帧即停。按 (id, filename) 双等，与 sumDatFrameBytesByDatFilepath 严格同口径。
     */
    private long scanContiguousDbBackedPrefixEnd(byte[] bytes, String filename) {
        long lastDbBackedEnd = 0L;
        int position = 0;
        while (position < bytes.length) {
            Frame frame = tryReadFrame(bytes, position);
            if (frame == null || !blockInfoRepository.existsByIdAndDatFilepath(frame.blockId(), filename)) {
                break;
            }
            lastDbBackedEnd = frame.end();
            position = (int) frame.end();
        }
        return lastDbBackedEnd;
    }

    /**
     * 整份文件是否可安全删除：当且仅当全文件能干净解析为若干完整帧、且无任一帧对应已入库区块。
     * 此处按区块 id <b>跨文件名</b>查 existsById（该文件名在库中无记录，却可能物理上承载了库记其它文件名的区块，
     * 必须跨文件名才能发现并保护）。一旦遇到无法解析的脏区(撕裂)，无法确认其后是否藏有库内区块，保守判为不可删。
     */
    private boolean isSafeToDeleteEntireOrphanFile(byte[] bytes) {
        int position = 0;
        while (position < bytes.length) {
            Frame frame = tryReadFrame(bytes, position);
            if (frame == null) {
                return false; // 脏区，无法确认其后是否藏有库内区块
            }
            if (blockInfoRepository.existsById(frame.blockId())) {
                return false; // 含库内区块，绝不删
            }
            position = (int) frame.end();
        }
        return true;
    }

    /** 尝试解析 position 处一帧；非干净完整帧（前缀不足/魔数错/长度非法/越界/区块体不可解析）返回 null。 */
    private Frame tryReadFrame(byte[] bytes, int position) {
        if (bytes.length - position < RECORD_PREFIX_BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int magic = buffer.getInt(position);
        if (magic != BlockConstants.MAGIC_NUMBER) {
            return null;
        }
        long rawLength = buffer.getLong(position + Integer.BYTES);
        if (rawLength < ParsedBlock.HEADER_SIZE || rawLength > MAX_REASONABLE_RAW_BLOCK_BYTES) {
            return null;
        }
        long end = (long) position + RECORD_PREFIX_BYTES + rawLength;
        if (end > bytes.length) {
            return null;
        }
        int rawStart = position + RECORD_PREFIX_BYTES;
        byte[] rawBlock = Arrays.copyOfRange(bytes, rawStart, rawStart + (int) rawLength);
        byte[] blockId;
        try {
            blockId = DatBlockReader.parseRawBlock(rawBlock).blockId();
        } catch (RuntimeException e) {
            return null; // 区块体不可解析（撕裂/损坏）
        }
        return new Frame(end, blockId);
    }

    private void truncate(Path datFile, long size) throws IOException {
        try (FileChannel channel = FileChannel.open(datFile, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        }
    }

    private List<Path> listDatFilesSortedByName(Path datDir) throws IOException {
        try (Stream<Path> paths = Files.list(datDir)) {
            return paths
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(BlockConstants.DAT_FILE_PREFIX) && name.endsWith(BlockConstants.DAT_FILE_SUFFIX);
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private record Frame(long end, byte[] blockId) {
    }
}
