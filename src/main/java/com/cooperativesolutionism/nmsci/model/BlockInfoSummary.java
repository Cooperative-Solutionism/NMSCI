package com.cooperativesolutionism.nmsci.model;

/**
 * 区块摘要投影（性能审计 QW3 / finding #5）。
 *
 * <p>运营/元数据端点 {@code /system/params}、{@code /system/status}、{@code /metadata/difficulty} 只读取最新区块的
 * 少量标量字段，并不需要整块 {@code raw_bytes}（与 .dat 内容重复、最大可达 block-max-size ~MB 级）。这些端点尤其
 * {@code /system/status} 常被轮询，若每次都物化 {@code raw_bytes} 会白白放大 DB 读取与 JVM 分配。
 *
 * <p>本闭合投影只声明所需 getter，Spring Data 仅 SELECT 对应列（排除 raw_bytes）。{@link BlockInfo} 实现本接口，
 * 使消费方（DTO 工厂等）对完整实体与投影代理均适用；序列化对外的 {@code /blocks/*} 仍返回完整实体，契约不变。
 */
public interface BlockInfoSummary {

    byte[] getId();

    Long getHeight();

    Long getTimestamp();

    Integer getRegisterDifficultyTarget();

    Integer getTransactionDifficultyTarget();
}
