package com.cooperativesolutionism.nmsci.block;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.DateUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BlockAssembler {

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private CentralPubkeyEmpowerMsgRepository centralPubkeyEmpowerMsgRepository;

    @Resource
    private CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    @Resource
    private FlowNodeRegisterMsgRepository flowNodeRegisterMsgRepository;

    @Resource
    private FlowNodeLockedMsgRepository flowNodeLockedMsgRepository;

    @Resource
    private TransactionRecordMsgRepository transactionRecordMsgRepository;

    @Resource
    private TransactionMountMsgRepository transactionMountMsgRepository;

    public AssembledBlock assemble(BlockInfo previousBlock, SelectedBlockMessages selectedMessages) {
        byte[] blockHeader = ArrayUtils.addAll(
                ByteArrayUtil.intToBytes(nmsciProperties.getBlockVersion()),
                ByteArrayUtil.longToBytes(nextHeight(previousBlock))
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.hexToBytes(nmsciProperties.getSourceCodeZipHash())
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                previousBlockHash(previousBlock)
        );

        byte[] blockBody = new byte[0];
        List<byte[]> leafTxids = new ArrayList<>();
        for (Map.Entry<MsgTypeEnum, List<MsgAbstract>> entry : selectedMessages.getMessagesByType().entrySet()) {
            MsgTypeEnum msgType = entry.getKey();
            List<MsgAbstract> msgAbstracts = entry.getValue();
            List<UUID> msgIds = new ArrayList<>();

            for (MsgAbstract msgAbstract : msgAbstracts) {
                msgIds.add(msgAbstract.getMsgId());
                msgAbstract.setIsInBlock(true);
            }

            blockBody = ArrayUtils.addAll(blockBody, ByteArrayUtil.longToBytes(msgAbstracts.size()));
            for (Message msg : findMessages(msgType, msgIds)) {
                leafTxids.add(msg.getTxid());
                blockBody = ArrayUtils.addAll(blockBody, msg.getRawBytes());
            }
        }

        byte[] merkleRoot = MerkleTreeUtil.calcMerkleRoot(leafTxids);
        long nowTimestamp = DateUtil.getCurrentMicros();

        blockHeader = ArrayUtils.addAll(blockHeader, merkleRoot);
        blockHeader = ArrayUtils.addAll(blockHeader, ByteArrayUtil.longToBytes(selectedMessages.getMaxMsgTimestamp()));
        blockHeader = ArrayUtils.addAll(blockHeader, ByteArrayUtil.intToBytes(nmsciProperties.getRegisterDifficultyTargetNbits()));
        blockHeader = ArrayUtils.addAll(blockHeader, ByteArrayUtil.intToBytes(nmsciProperties.getTransactionDifficultyTargetNbits()));
        blockHeader = ArrayUtils.addAll(blockHeader, ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPubkeyBase64()));
        blockHeader = ArrayUtils.addAll(blockHeader, ByteArrayUtil.longToBytes(nowTimestamp));

        try {
            byte[] centralPrikey = ByteArrayUtil.base64ToBytes(nmsciProperties.getCentralPrikeyBase64());
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(
                    Secp256k1EncryptUtil.signData(blockHeader, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey))
            );
            blockHeader = ArrayUtils.addAll(blockHeader, centralSignature);

            byte[] rawBlockBytes = ArrayUtils.addAll(blockHeader, blockBody);
            BlockInfo blockInfo = buildBlockInfo(
                    previousBlock,
                    merkleRoot,
                    selectedMessages.getMaxMsgTimestamp(),
                    nowTimestamp,
                    centralSignature,
                    blockHeader,
                    rawBlockBytes
            );
            byte[] datBytes = ArrayUtils.addAll(
                    ArrayUtils.addAll(
                            ByteArrayUtil.intToBytes(BlockConstants.MAGIC_NUMBER),
                            ByteArrayUtil.longToBytes(rawBlockBytes.length)
                    ),
                    rawBlockBytes
            );

            return new AssembledBlock(blockInfo, datBytes, selectedMessages.getAllMessages());
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

    private List<Message> findMessages(MsgTypeEnum msgType, List<UUID> msgIds) {
        List<Message> messages = new ArrayList<>();
        if (msgIds.isEmpty()) {
            return messages;
        }

        if (msgType.equals(MsgTypeEnum.FlowNodeRegisterMsg)) {
            messages.addAll(flowNodeRegisterMsgRepository.findAllById(msgIds));
        } else if (msgType.equals(MsgTypeEnum.CentralPubkeyEmpowerMsg)) {
            messages.addAll(centralPubkeyEmpowerMsgRepository.findAllById(msgIds));
        } else if (msgType.equals(MsgTypeEnum.CentralPubkeyLockedMsg)) {
            messages.addAll(centralPubkeyLockedMsgRepository.findAllById(msgIds));
        } else if (msgType.equals(MsgTypeEnum.FlowNodeLockedMsg)) {
            messages.addAll(flowNodeLockedMsgRepository.findAllById(msgIds));
        } else if (msgType.equals(MsgTypeEnum.TransactionRecordMsg)) {
            messages.addAll(transactionRecordMsgRepository.findAllById(msgIds));
        } else if (msgType.equals(MsgTypeEnum.TransactionMountMsg)) {
            messages.addAll(transactionMountMsgRepository.findAllById(msgIds));
        }

        return messages;
    }
}
