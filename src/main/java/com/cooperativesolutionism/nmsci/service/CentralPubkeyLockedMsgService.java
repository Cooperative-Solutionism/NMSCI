package com.cooperativesolutionism.nmsci.service;

import static com.cooperativesolutionism.nmsci.constant.ProtocolByteLengths.COMPRESSED_PUBLIC_KEY_BYTES;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.exception.ConflictException;
import com.cooperativesolutionism.nmsci.exception.NotFoundException;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.util.Optional;
import java.util.UUID;

@Service
@Validated
public class CentralPubkeyLockedMsgService {

    private static final Logger logger = LoggerFactory.getLogger(CentralPubkeyLockedMsgService.class);

    @Resource
    private NmsciProperties nmsciProperties;

    @Resource
    private CentralPubkeyLockedMsgRepository centralPubkeyLockedMsgRepository;

    @Resource
    private MsgAbstractService msgAbstractService;

    @Resource
    private SignatureValidator signatureValidator;

    @Resource
    private ProtocolRawBytesBuilder protocolRawBytesBuilder;

    @Resource
    private CentralSignatureService centralSignatureService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private CentralPubkeyLockShutdownService shutdownService;

    @Resource
    private BlockChainService blockChainService;

    public void saveCentralPubkeyLockedMsg(@Valid @Nonnull CentralPubkeyLockedMsg centralPubkeyLockedMsg) {
        String centralPubkeyBase64 = nmsciProperties.getCentralPubkeyBase64();

        if (centralPubkeyLockedMsg.getMsgType() != MsgTypeEnum.CentralPubkeyLockedMsg.getValue()) {
            throw new IllegalArgumentException("信息类型错误，必须为" + MsgTypeEnum.CentralPubkeyLockedMsg.getValue());
        }

        if (centralPubkeyLockedMsgRepository.existsById(centralPubkeyLockedMsg.getId())) {
            throw new ConflictException("该中心公钥冻结信息id(" + centralPubkeyLockedMsg.getId() + ")已存在");
        }

        if (centralPubkeyLockedMsgRepository.existsByCentralPubkey(centralPubkeyLockedMsg.getCentralPubkey())) {
            throw new ConflictException("该中心公钥(" + ByteArrayUtil.bytesToBase64(centralPubkeyLockedMsg.getCentralPubkey()) + ")已被冻结");
        }

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        if (!java.util.Arrays.equals(centralPubkeyLockedMsg.getCentralPubkey(), centralPubkey)) {
            throw new IllegalArgumentException("中心公钥设置错误，当前中心公钥为:(" + centralPubkeyBase64 + ")");
        }

        signatureValidator.validateLowS(centralPubkeyLockedMsg.getCentralSignaturePre(), "中心预签名不符合低S标准");

        byte[] verifyData = protocolRawBytesBuilder.centralPubkeyLockedVerifyData(centralPubkeyLockedMsg);
        signatureValidator.validateSignature(
                verifyData,
                centralPubkeyLockedMsg.getCentralSignaturePre(),
                centralPubkeyLockedMsg.getCentralPubkey(),
                "中心预签名验证失败"
        );
        centralSignatureService.signAndPopulate(
                centralPubkeyLockedMsg,
                verifyData,
                centralPubkeyLockedMsg.getCentralSignaturePre()
        );

        transactionTemplate.executeWithoutResult(status -> {
            centralPubkeyLockedMsgRepository.save(centralPubkeyLockedMsg);
            msgAbstractService.saveMsgAbstract(centralPubkeyLockedMsg);
        });

        blockChainService.generateBlockUntilNoNotInBlockMsgs();

        logger.warn("中心公钥冻结成功，所有未装块的信息装块成功，程序即将优雅终止");
        shutdownService.requestShutdown();
    }
    public CentralPubkeyLockedMsg getCentralPubkeyLockedMsgById(UUID id) {
        return EntityLookup.requireById(id, "中心公钥冻结信息", centralPubkeyLockedMsgRepository::findById);
    }
    public CentralPubkeyLockedMsg getCentralPubkeyLockedMsgByCentralPubkey(byte[] centralPubkey) {
        return findCentralPubkeyLockedMsgByCentralPubkey(centralPubkey)
                .orElseThrow(() -> new NotFoundException("中心公钥(" + ByteArrayUtil.bytesToHex(centralPubkey) + ")未冻结"));
    }
    public Optional<CentralPubkeyLockedMsg> findCentralPubkeyLockedMsgByCentralPubkey(byte[] centralPubkey) {
        if (centralPubkey == null || centralPubkey.length != COMPRESSED_PUBLIC_KEY_BYTES) {
            throw new IllegalArgumentException("中心公钥不能为空或长度不为33字节");
        }

        return Optional.ofNullable(centralPubkeyLockedMsgRepository.findByCentralPubkey(centralPubkey));
    }
    public Slice<CentralPubkeyLockedMsg> listCentralPubkeyLockedMsgs(Pageable pageable) {
        return centralPubkeyLockedMsgRepository.findAll(pageable);
    }
}
