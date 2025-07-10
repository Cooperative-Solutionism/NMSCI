package com.cooperativesolutionism.nmsci.service.impl;

import com.cooperativesolutionism.nmsci.constant.BlockConstants;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.Message;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.*;
import com.cooperativesolutionism.nmsci.service.BlockChainService;
import com.cooperativesolutionism.nmsci.util.*;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class BlockChainServiceImpl implements BlockChainService {
    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Value("${block-version}")
    private int blockVersion;

    @Value("${block-header-size}")
    private int blockHeaderSize;

    @Value("${block-max-size}")
    private long blockMaxSize;

    @Value("${blcok-dat-max-size}")
    private long blockDatMaxSize;

    @Value("${file-root-dir}")
    private String fileRootDir;

    @Value("${file-dat-dir}")
    private String fileDatDir;

    @Value("${file-source-code-dir}")
    private String fileSourceCodeDir;

    @Value("${source-code-zip-hash}")
    private String sourceCodeZipHash;

    @Value("${register-difficulty-target-nbits}")
    private int registerDifficultyTargetNbits;

    @Value("${transaction-difficulty-target-nbits}")
    private int transactionDifficultyTargetNbits;

    @Resource
    private BlockInfoRepository blockInfoRepository;

    @Resource
    private MsgAbstractRepository msgAbstractRepository;

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

    @Override
    @Transactional
    // TODO：需要增加不得加任何信息的区块生成功能
    public void generateBlock() {
        // 【版本号4字节(0x1)】+【区块高度8字节】+【相应版本全代码压缩包(包含协议文本)sha256hash32字节】
        // +【前区块头的dblsha256hash32字节】+【所有信息默克尔根32字节】+【信息内最大时间戳8字节】
        // +【流转节点注册难度目标4字节】+【消费节点交易难度目标4字节】
        // +【中心公钥32字节】+【固定区块时间戳8字节】+【中心公钥对前述所有信息签名64字节】
        // +【流转节点注册信息数量8字节】+【流转节点注册信息123字节】*n
        // +【中心公钥公证信息数量8字节】+【中心公钥公证信息220字节】*n
        // +【中心公钥冻结信息数量8字节】+【中心公钥冻结信息187字节】*n
        // +【流转节点冻结信息数量8字节】+【流转节点冻结信息220字节】*n
        // +【交易记录信息数量8字节】+【交易记录信息335字节】*n
        // +【交易挂载信息数量8字节】+【交易挂载信息341字节】*n
        byte[] blockHeader;
        byte[] blockBody = new byte[0];
        int blockSize = 0;
        long height = 0L;
        byte[] previousBlockHash = ByteArrayUtil.hexToBytes(BlockConstants.GENESIS_HASH);
        byte[] merkleRoot = new byte[32];
        long maxMsgTimestamp = 0L;
        String datFilepathStr = "";
        BlockInfo blockInfo = new BlockInfo();

        blockInfo.setVersion(blockVersion);

        BlockInfo newestBlockInfo = blockInfoRepository.findTopByOrderByHeightDesc();
        if (newestBlockInfo != null) {
            height = newestBlockInfo.getHeight() + 1;
            previousBlockHash = newestBlockInfo.getId();
            datFilepathStr = newestBlockInfo.getDatFilepath();
        }

        blockInfo.setHeight(height);
        blockInfo.setSourceCodeZipHash(ByteArrayUtil.hexToBytes(sourceCodeZipHash));
        blockInfo.setPreviousBlockHash(previousBlockHash);

        blockSize += blockHeaderSize;

        int page = 0;
        int pageSize = 1000;
        Map<MsgTypeEnum, ArrayList<MsgAbstract>> needBlockedMsgAbstractsMap = new LinkedHashMap<>();

        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            needBlockedMsgAbstractsMap.put(msgType, new ArrayList<>());
        }

        outerLoop:
        while (true) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<MsgAbstract> msgAbstractRes = msgAbstractRepository.findByIsInBlockFalseOrderByConfirmTimestampAsc(pageable);
            List<MsgAbstract> msgAbstracts = msgAbstractRes.getContent();
            if (msgAbstracts.isEmpty()) {
                break;
            }

            for (MsgAbstract msgAbstract : msgAbstracts) {
                blockSize += MsgTypeEnum.getSizeByValue(msgAbstract.getMsgType());
                if (blockSize > blockMaxSize) {
                    break outerLoop;
                }
                maxMsgTimestamp = Math.max(maxMsgTimestamp, msgAbstract.getConfirmTimestamp());
                needBlockedMsgAbstractsMap.get(MsgTypeEnum.getByValue(msgAbstract.getMsgType())).add(msgAbstract);
            }

            page++;
        }

        blockHeader = ArrayUtils.addAll(
                ByteArrayUtil.intToBytes(blockVersion),
                ByteArrayUtil.longToBytes(height)
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.hexToBytes(sourceCodeZipHash)
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                previousBlockHash
        );

        if (!needBlockedMsgAbstractsMap.isEmpty()) {
            List<byte[]> leafTxids = new ArrayList<>();
            for (Map.Entry<MsgTypeEnum, ArrayList<MsgAbstract>> entry : needBlockedMsgAbstractsMap.entrySet()) {
                MsgTypeEnum msgType = entry.getKey();
                ArrayList<MsgAbstract> msgAbstracts = entry.getValue();
                List<UUID> msgIds = new ArrayList<>();
                List<Message> allMsgs = new ArrayList<>();

                for (MsgAbstract msgAbstract : msgAbstracts) {
                    msgIds.add(msgAbstract.getMsgId());
                    msgAbstract.setIsInBlock(true);
                }

                if (msgType.equals(MsgTypeEnum.CentralPubkeyEmpowerMsg)) {
                    allMsgs.addAll(centralPubkeyEmpowerMsgRepository.findAllById(msgIds));
                } else if (msgType.equals(MsgTypeEnum.CentralPubkeyLockedMsg)) {
                    allMsgs.addAll(centralPubkeyLockedMsgRepository.findAllById(msgIds));
                } else if (msgType.equals(MsgTypeEnum.FlowNodeRegisterMsg)) {
                    allMsgs.addAll(flowNodeRegisterMsgRepository.findAllById(msgIds));
                } else if (msgType.equals(MsgTypeEnum.FlowNodeLockedMsg)) {
                    allMsgs.addAll(flowNodeLockedMsgRepository.findAllById(msgIds));
                } else if (msgType.equals(MsgTypeEnum.TransactionRecordMsg)) {
                    allMsgs.addAll(transactionRecordMsgRepository.findAllById(msgIds));
                } else if (msgType.equals(MsgTypeEnum.TransactionMountMsg)) {
                    allMsgs.addAll(transactionMountMsgRepository.findAllById(msgIds));
                }

                // 信息数量
                blockBody = ArrayUtils.addAll(
                        blockBody,
                        ByteArrayUtil.longToBytes(msgAbstracts.size())
                );

                for (Message msg : allMsgs) {
                    leafTxids.add(msg.getTxid());
                    blockBody = ArrayUtils.addAll(
                            blockBody,
                            msg.getRawBytes()
                    );
                }

                // 更新消息摘要为已入块
                msgAbstractRepository.saveAll(msgAbstracts);
            }

            // 计算默克尔根
            merkleRoot = MerkleTreeUtil.calcMerkleRoot(leafTxids);
        }

        long nowTimestamp = DateUtil.getCurrentMicros();
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                merkleRoot
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.longToBytes(maxMsgTimestamp)
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.intToBytes(registerDifficultyTargetNbits)
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits)
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.base64ToBytes(centralPubkeyBase64)
        );
        blockHeader = ArrayUtils.addAll(
                blockHeader,
                ByteArrayUtil.longToBytes(nowTimestamp)
        );

        try {
            byte[] centralPrikey = ByteArrayUtil.base64ToBytes(centralPrikeyBase64);
            byte[] centralSignature = Secp256k1EncryptUtil.derToRs(Secp256k1EncryptUtil.signData(blockHeader, Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey)));
            blockHeader = ArrayUtils.addAll(
                    blockHeader,
                    centralSignature
            );

            blockInfo.setMerkleRoot(merkleRoot);
            blockInfo.setMaxMsgTimestamp(maxMsgTimestamp);
            blockInfo.setRegisterDifficultyTarget(registerDifficultyTargetNbits);
            blockInfo.setTransactionDifficultyTarget(transactionDifficultyTargetNbits);
            blockInfo.setCentralPubkey(ByteArrayUtil.base64ToBytes(centralPubkeyBase64));
            blockInfo.setTimestamp(nowTimestamp);
            blockInfo.setCentralSignature(centralSignature);
            blockInfo.setId(Sha256Util.doubleDigest(blockHeader));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        byte[] block = ArrayUtils.addAll(
                blockHeader,
                blockBody
        );

        blockInfo.setRawBytes(block);

        // 拼接魔数
        byte[] blockLeading = ArrayUtils.addAll(
                ByteArrayUtil.intToBytes(BlockConstants.MAGIC_NUMBER),
                ByteArrayUtil.longToBytes(block.length)
        );
        block = ArrayUtils.addAll(
                blockLeading,
                block
        );

        // 将block字节数据保存至.dat文件
        if (datFilepathStr == null || datFilepathStr.isEmpty()) {
            Path path = Paths.get(fileRootDir, fileDatDir, "blk" + String.format("%08d", 0) + ".dat");
            datFilepathStr = path.toString();
        }

        String rootDir = System.getProperty("user.dir");
        Path datFilepath = Paths.get(rootDir, datFilepathStr);
        Path fileDir = datFilepath.getParent();
        if (!Files.exists(fileDir)) {
            try {
                Files.createDirectories(fileDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            // 查看文件大小是否超过限制
            if (Files.exists(datFilepath) && Files.size(datFilepath) + block.length > blockDatMaxSize) {
                String[] parts = datFilepathStr.split("\\\\");
                String lastPart = parts[parts.length - 1];
                String indexStr = lastPart.replace("blk", "").replace(".dat", "");
                int index = Integer.parseInt(indexStr);
                datFilepath = Paths.get(fileRootDir, fileDatDir, "blk" + String.format("%08d", index + 1) + ".dat");
                datFilepathStr = datFilepath.toString();
            }

            // 追加写入.dat文件
            if (!Files.exists(datFilepath)) {
                Files.createFile(datFilepath);
            }
            byte[] existingData = Files.readAllBytes(datFilepath);
            block = ArrayUtils.addAll(existingData, block);

            // 写入.dat文件
            Files.write(datFilepath, block);

            blockInfo.setDatFilepath(datFilepathStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            Path sourceCodePath = Paths.get(fileRootDir, fileSourceCodeDir, "source_code_v" + blockVersion + ".zip");
            // 检测文件是否存在
            if (!Files.exists(sourceCodePath)) {
                // 不存在则读取static文件夹中的source_code文件内容，复制到source_code文件夹
                ClassPathResource classPathResource = new ClassPathResource("static/source_code_v" + blockVersion + ".zip");
                // 确保父目录存在
                if (!Files.exists(sourceCodePath.getParent())) {
                    Files.createDirectories(sourceCodePath.getParent());
                }
                Files.copy(classPathResource.getFile().toPath(), sourceCodePath);
            }

            blockInfo.setSourceCodeZipFilepath(sourceCodePath.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy source code zip file", e);
        }

        blockInfoRepository.save(blockInfo);
    }
}
