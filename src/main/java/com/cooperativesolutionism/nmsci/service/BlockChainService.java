package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.block.AssembledBlock;
import com.cooperativesolutionism.nmsci.block.BlockAssembler;
import com.cooperativesolutionism.nmsci.block.BlockFileReconciler;
import com.cooperativesolutionism.nmsci.block.BlockFileStore;
import com.cooperativesolutionism.nmsci.block.BlockGenerationLock;
import com.cooperativesolutionism.nmsci.block.BlockMessageSelector;
import com.cooperativesolutionism.nmsci.block.SelectedBlockMessages;
import com.cooperativesolutionism.nmsci.block.SourceCodeArchiveStore;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.monitoring.NmsciMetrics;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.BlockInfoSummary;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BlockChainService {
    private final BlockInfoRepository blockInfoRepository;
    private final MsgAbstractRepository msgAbstractRepository;
    private final BlockMessageSelector blockMessageSelector;
    private final BlockAssembler blockAssembler;
    private final BlockFileStore blockFileStore;
    private final SourceCodeArchiveStore sourceCodeArchiveStore;
    private final BlockGenerationLock blockGenerationLock;
    private final NmsciMetrics nmsciMetrics;
    private final BlockFileReconciler blockFileReconciler;
    private final AtomicBoolean startupReconciled = new AtomicBoolean(false);

    public BlockChainService(
            BlockInfoRepository blockInfoRepository,
            MsgAbstractRepository msgAbstractRepository,
            BlockMessageSelector blockMessageSelector,
            BlockAssembler blockAssembler,
            BlockFileStore blockFileStore,
            SourceCodeArchiveStore sourceCodeArchiveStore,
            BlockGenerationLock blockGenerationLock,
            NmsciMetrics nmsciMetrics,
            BlockFileReconciler blockFileReconciler
    ) {
        this.blockInfoRepository = blockInfoRepository;
        this.msgAbstractRepository = msgAbstractRepository;
        this.blockMessageSelector = blockMessageSelector;
        this.blockAssembler = blockAssembler;
        this.blockFileStore = blockFileStore;
        this.sourceCodeArchiveStore = sourceCodeArchiveStore;
        this.blockGenerationLock = blockGenerationLock;
        this.nmsciMetrics = nmsciMetrics;
        this.blockFileReconciler = blockFileReconciler;
    }

    @Transactional
    public void generateBlock() {
        blockGenerationLock.lock();
        ensureStartupReconciled();
        generateSelectedBlock(blockMessageSelector.select());
    }

    private boolean generateBlockIfMessagesSelectedForLoop() {
        SelectedBlockMessages selectedMessages = blockMessageSelector.select();
        if (selectedMessages.isEmpty()) {
            return false;
        }

        generateSelectedBlock(selectedMessages);
        return true;
    }

    private void generateSelectedBlock(SelectedBlockMessages selectedMessages) {
        BlockInfo previousBlock = blockInfoRepository.findTopByOrderByHeightDesc();
        AssembledBlock assembledBlock = blockAssembler.assemble(previousBlock, selectedMessages);
        BlockInfo blockInfo = assembledBlock.getBlockInfo();

        markSelectedInBlock(assembledBlock);
        msgAbstractRepository.saveAll(assembledBlock.getSelectedMsgAbstracts());
        blockInfo.setDatFilepath(blockFileStore.appendBlock(previousDatFilepath(previousBlock), assembledBlock.getDatBytes()));
        blockInfo.setSourceCodeZipFilepath(sourceCodeArchiveStore.copyArchiveForVersion(blockInfo.getVersion()));
        blockInfoRepository.save(blockInfo);

        // 仅在事务成功提交后记录指标，避免回滚时计入「幻影区块」/ 高度网关超前于已持久化的链。
        recordBlockMetricsOnCommit(
                blockInfo.getRawBytes().length,
                assembledBlock.getSelectedMsgAbstracts().size(),
                blockInfo.getHeight()
        );
    }

    private void recordBlockMetricsOnCommit(long sizeBytes, long messageCount, long height) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    nmsciMetrics.recordGeneratedBlock(sizeBytes, messageCount, height);
                }
            });
        } else {
            nmsciMetrics.recordGeneratedBlock(sizeBytes, messageCount, height);
        }
    }
    @Transactional
    public void generateBlockUntilNoNotInBlockMsgs() {
        blockGenerationLock.lock();
        ensureStartupReconciled();
        while (generateBlockIfMessagesSelectedForLoop()) {
            // Continue until the selector returns no messages.
        }
    }
    public BlockInfo getLastBlock() {
        return blockInfoRepository.findTopByOrderByHeightDesc();
    }
    /**
     * 最新区块的摘要投影（性能审计 QW3）：供只读少量标量字段的运营/元数据端点使用，避免物化 raw_bytes。
     * 对外序列化整块的 /blocks/* 仍走 {@link #getLastBlock()}，契约不变。
     */
    public BlockInfoSummary getLastBlockSummary() {
        return blockInfoRepository.findFirstByOrderByHeightDesc();
    }
    public BlockInfo getBlockByHeight(long height) {
        BlockInfo blockInfo = blockInfoRepository.findByHeight(height);
        if (blockInfo == null) {
            throw new NotFoundException("区块高度(" + height + ")不存在");
        }

        return blockInfo;
    }
    public BlockInfo getBlockByHash(String hash) {
        return blockInfoRepository.findById(ByteArrayUtil.hexToBytes(hash)).orElseThrow(
                () -> new NotFoundException("该区块不存在: " + hash)
        );
    }

    /**
     * 首次出块前（任一出块路径：定时任务 generateBlock 或中心公钥冻结的同步出块 generateBlockUntilNoNotInBlockMsgs）
     * 执行一次 .dat 与数据库的崩溃对账。在 {@link BlockGenerationLock} 持锁之后调用，故严格早于本进程任何 appendBlock，
     * 且与所有出块路径跨实例串行（对账期间无并发写），满足 {@link BlockFileReconciler} 的「孤儿必在尾部」前提。
     * CAS 保证整个进程生命周期仅跑一次。
     */
    private void ensureStartupReconciled() {
        if (startupReconciled.compareAndSet(false, true)) {
            blockFileReconciler.reconcileOnStartup();
        }
    }

    private String previousDatFilepath(BlockInfo previousBlock) {
        if (previousBlock == null) {
            return null;
        }
        return previousBlock.getDatFilepath();
    }

    private void markSelectedInBlock(AssembledBlock assembledBlock) {
        for (MsgAbstract msgAbstract : assembledBlock.getSelectedMsgAbstracts()) {
            msgAbstract.setIsInBlock(true);
        }
    }

}
