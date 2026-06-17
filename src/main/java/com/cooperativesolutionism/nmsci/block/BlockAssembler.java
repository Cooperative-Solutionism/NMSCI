package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BlockAssembler {

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private BlockMessagePayloadFetcher blockMessagePayloadFetcher;

    public AssembledBlock assemble(BlockInfo previousBlock, SelectedBlockMessages selectedMessages) {
        ByteArrayOutputStream blockHeader = new ByteArrayOutputStream(nmsciProperties.getBlockHeaderSize());
        write(blockHeader, ByteArrayUtil.intToBytes(nmsciProperties.getBlockVersion()));
        write(blockHeader, ByteArrayUtil.longToBytes(nextHeight(previousBlock)));
        write(blockHeader, ByteArrayUtil.hexToBytes(nmsciProperties.getSourceCodeZipHash()));
        write(blockHeader, previousBlockHash(previousBlock));

        ByteArrayOutputStream blockBody = new ByteArrayOutputStream();
        List<byte[]> leafTxids = new ArrayList<>();
        for (Map.Entry<MsgTypeEnum, List<MsgAbstract>> entry : selectedMessages.getMessagesByType().entrySet()) {
            MsgTypeEnum msgType = entry.getKey();
            List<MsgAbstract> msgAbstracts = entry.getValue();
            List<UUID> msgIds = messageIds(msgAbstracts);

            write(blockBody, ByteArrayUtil.longToBytes(msgAbstracts.size()));
            for (BlockMessagePayload msg : blockMessagePayloadFetcher.findPayloads(msgType, msgIds)) {
                leafTxids.add(msg.getTxid());
                write(blockBody, msg.getRawBytes());
            }
        }

        byte[] merkleRoot = MerkleTreeUtil.calcMerkleRoot(leafTxids);
        long nowTimestamp = DateUtil.getCurrentMicros();

        write(blockHeader, merkleRoot);
        write(blockHeader, ByteArrayUtil.longToBytes(selectedMessages.getMaxMsgTimestamp()));
        write(blockHeader, ByteArrayUtil.intToBytes(nmsciProperties.getRegisterDifficultyTargetNbits()));
        write(blockHeader, ByteArrayUtil.intToBytes(nmsciProperties.getTransactionDifficultyTargetNbits()));
        write(blockHeader, ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPubkeyBase64()));
        write(blockHeader, ByteArrayUtil.longToBytes(nowTimestamp));

        try {
            byte[] blockHeaderForSigning = blockHeader.toByteArray();
            byte[] centralPrikey = ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPrikeyBase64());
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(
                    Secp256k1EncryptUtil.signData(blockHeaderForSigning, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey))
            );
            write(blockHeader, centralSignature);

            byte[] blockHeaderBytes = blockHeader.toByteArray();
            byte[] blockBodyBytes = blockBody.toByteArray();
            ByteArrayOutputStream rawBlock = new ByteArrayOutputStream(blockHeaderBytes.length + blockBodyBytes.length);
            write(rawBlock, blockHeaderBytes);
            write(rawBlock, blockBodyBytes);
            byte[] rawBlockBytes = rawBlock.toByteArray();
            BlockInfo blockInfo = buildBlockInfo(
                    previousBlock,
                    merkleRoot,
                    selectedMessages.getMaxMsgTimestamp(),
                    nowTimestamp,
                    centralSignature,
                    blockHeaderBytes,
                    rawBlockBytes
            );
            ByteArrayOutputStream dat = new ByteArrayOutputStream(Integer.BYTES + Long.BYTES + rawBlockBytes.length);
            write(dat, ByteArrayUtil.intToBytes(BlockConstants.MAGIC_NUMBER));
            write(dat, ByteArrayUtil.longToBytes(rawBlockBytes.length));
            write(dat, rawBlockBytes);

            markSelectedInBlock(selectedMessages);
            return new AssembledBlock(blockInfo, dat.toByteArray(), selectedMessages.getAllMessages());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BlockInfo buildBlockInfo(
            BlockInfo previousBlock,
            byte[] merkleRoot,
            long maxMsgTimestamp,
            long nowTimestamp,
            byte[] centralSignature,
            byte[] blockHeader,
            byte[] rawBlockBytes
    ) {
        BlockInfo blockInfo = new BlockInfo();
        blockInfo.setVersion(nmsciProperties.getBlockVersion());
        blockInfo.setHeight(nextHeight(previousBlock));
        blockInfo.setSourceCodeZipHash(ByteArrayUtil.hexToBytes(nmsciProperties.getSourceCodeZipHash()));
        blockInfo.setPreviousBlockHash(previousBlockHash(previousBlock));
        blockInfo.setMerkleRoot(merkleRoot);
        blockInfo.setMaxMsgTimestamp(maxMsgTimestamp);
        blockInfo.setRegisterDifficultyTarget(nmsciProperties.getRegisterDifficultyTargetNbits());
        blockInfo.setTransactionDifficultyTarget(nmsciProperties.getTransactionDifficultyTargetNbits());
        blockInfo.setCentralPubkey(ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPubkeyBase64()));
        blockInfo.setTimestamp(nowTimestamp);
        blockInfo.setCentralSignature(centralSignature);
        blockInfo.setId(Sha256Util.doubleDigest(blockHeader));
        blockInfo.setRawBytes(rawBlockBytes);
        return blockInfo;
    }

    private long nextHeight(BlockInfo previousBlock) {
        if (previousBlock == null) {
            return 0L;
        }
        return previousBlock.getHeight() + 1;
    }

    private byte[] previousBlockHash(BlockInfo previousBlock) {
        if (previousBlock == null) {
            return ByteArrayUtil.hexToBytes(BlockConstants.GENESIS_HASH);
        }
        return previousBlock.getId();
    }

    private List<UUID> messageIds(List<MsgAbstract> msgAbstracts) {
        List<UUID> msgIds = new ArrayList<>(msgAbstracts.size());
        for (MsgAbstract msgAbstract : msgAbstracts) {
            msgIds.add(msgAbstract.getMsgId());
        }
        return msgIds;
    }

    private void markSelectedInBlock(SelectedBlockMessages selectedMessages) {
        for (MsgAbstract msgAbstract : selectedMessages.getAllMessages()) {
            msgAbstract.setIsInBlock(true);
        }
    }

    private void write(ByteArrayOutputStream outputStream, byte[] bytes) {
        outputStream.writeBytes(bytes);
    }
}
