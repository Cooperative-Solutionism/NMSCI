package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;

import java.util.List;

public class AssembledBlock {

    private final BlockInfo blockInfo;
    private final byte[] datBytes;
    private final List<MsgAbstract> selectedMsgAbstracts;

    public AssembledBlock(BlockInfo blockInfo, byte[] datBytes, List<MsgAbstract> selectedMsgAbstracts) {
        this.blockInfo = blockInfo;
        this.datBytes = datBytes;
        this.selectedMsgAbstracts = selectedMsgAbstracts;
    }

    public BlockInfo getBlockInfo() {
        return blockInfo;
    }

    public byte[] getDatBytes() {
        return datBytes;
    }

    public List<MsgAbstract> getSelectedMsgAbstracts() {
        return selectedMsgAbstracts;
    }
}
