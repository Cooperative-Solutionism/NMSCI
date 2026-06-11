package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.block.AssembledBlock;
import com.cooperativesolutionism.nmsci.block.BlockAssembler;
import com.cooperativesolutionism.nmsci.block.BlockFileStore;
import com.cooperativesolutionism.nmsci.block.BlockMessageSelector;
import com.cooperativesolutionism.nmsci.block.SelectedBlockMessages;
import com.cooperativesolutionism.nmsci.block.SourceCodeArchiveStore;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.repository.BlockInfoRepository;
import com.cooperativesolutionism.nmsci.repository.MsgAbstractRepository;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockChainServiceImpl implements BlockChainService {
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


    @Override
    @Transactional
    public void generateBlock() {
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

    @Override
    @Transactional
    public void generateBlockUntilNoNotInBlockMsgs() {
        while (generateBlockIfMessagesSelectedForLoop()) {
            // Continue until the selector returns no messages.
        }
    }

    @Override
    public BlockInfo getLastBlock() {
        return blockInfoRepository.findTopByOrderByHeightDesc();
    }

    @Override
    public BlockInfo getBlockByHeight(long height) {
        BlockInfo blockInfo = blockInfoRepository.findByHeight(height);
        if (blockInfo == null) {
            throw new IllegalArgumentException("区块高度(" + height + ")不存在");
        }

        return blockInfo;
    }

    @Override
    public BlockInfo getBlockByHash(String hash) {
        return blockInfoRepository.findById(ByteArrayUtil.hexToBytes(hash)).orElseThrow(
                () -> new IllegalArgumentException("该区块不存在: " + hash)
        );
    }

    private String previousDatFilepath(BlockInfo previousBlock) {
        if (previousBlock == null) {
            return null;
        }
        return previousBlock.getDatFilepath();
    }

}
