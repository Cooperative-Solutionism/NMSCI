package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.block.AssembledBlock;
import com.cooperativesolutionism.nmsci.block.BlockAssembler;
import com.cooperativesolutionism.nmsci.block.BlockFileStore;
import com.cooperativesolutionism.nmsci.block.BlockGenerationLock;
import com.cooperativesolutionism.nmsci.block.BlockMessageSelector;
import com.cooperativesolutionism.nmsci.block.SelectedBlockMessages;
import com.cooperativesolutionism.nmsci.block.SourceCodeArchiveStore;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockChainService {
    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private MsgAbstractRepository msgAbstractRepository;

    @Resource
    private BlockMessageSelector blockMessageSelector;

    @Resource
    private BlockAssembler blockAssembler;

    @Resource
    private BlockFileStore blockFileStore;

    @Resource
    private SourceCodeArchiveStore sourceCodeArchiveStore;

    @Resource
    private BlockGenerationLock blockGenerationLock;
    @Transactional
    public void generateBlock() {
        blockGenerationLock.lock();
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

        msgAbstractRepository.saveAll(assembledBlock.getSelectedMsgAbstracts());
        blockInfo.setDatFilepath(blockFileStore.appendBlock(previousDatFilepath(previousBlock), assembledBlock.getDatBytes()));
        blockInfo.setSourceCodeZipFilepath(sourceCodeArchiveStore.copyArchiveForVersion(blockInfo.getVersion()));
        blockInfoRepository.save(blockInfo);
    }
    @Transactional
    public void generateBlockUntilNoNotInBlockMsgs() {
        blockGenerationLock.lock();
        while (generateBlockIfMessagesSelectedForLoop()) {
            // Continue until the selector returns no messages.
        }
    }
    public BlockInfo getLastBlock() {
        return blockInfoRepository.findTopByOrderByHeightDesc();
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

    private String previousDatFilepath(BlockInfo previousBlock) {
        if (previousBlock == null) {
            return null;
        }
        return previousBlock.getDatFilepath();
    }

}
