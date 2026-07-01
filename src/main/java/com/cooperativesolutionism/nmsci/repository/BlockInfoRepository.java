package com.cooperativesolutionism.nmsci.repository;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.BlockInfoSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockInfoRepository extends JpaRepository<BlockInfo, byte[]> {

    BlockInfo findTopByOrderByHeightDesc();

    /**
     * 最新区块的摘要投影（性能审计 QW3）：闭合投影只 SELECT 摘要字段，排除 raw_bytes。
     * 与 {@link #findTopByOrderByHeightDesc()} 同为「按高度倒序取首条」，供只读标量的运营/元数据端点使用。
     */
    BlockInfoSummary findFirstByOrderByHeightDesc();

    BlockInfo findByHeight(Long height);

    /**
     * 某 .dat 文件名下、DB 已记录的全部区块在 .dat 中应占的总字节数（启动对账用，DB 为链位置唯一真相源）。
     * 每条记录 = 12 字节记录前缀（4 魔数 + 8 长度）+ 原始区块字节，故为 {@code SUM(octet_length(raw_bytes) + 12)}。
     * 无对应区块时返回 0。{@code octet_length} 为 PostgreSQL bytea 字节长度函数。
     */
    @Query(value = "SELECT COALESCE(SUM(octet_length(raw_bytes) + 12), 0) FROM block_infos WHERE dat_filepath = :datFilepath", nativeQuery = true)
    long sumDatFrameBytesByDatFilepath(@Param("datFilepath") String datFilepath);

    /**
     * 某区块是否已入库且其 dat_filepath 恰为指定文件名（启动对账截断分支用，与 sumDatFrameBytesByDatFilepath 同口径，
     * 避免把别文件名下的区块误算进本文件的连续库内前缀）。
     */
    boolean existsByIdAndDatFilepath(byte[] id, String datFilepath);
}
