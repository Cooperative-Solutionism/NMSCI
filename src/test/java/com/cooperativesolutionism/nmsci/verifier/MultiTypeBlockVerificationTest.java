package com.cooperativesolutionism.nmsci.verifier;

import com.cooperativesolutionism.nmsci.block.AssembledBlock;
import com.cooperativesolutionism.nmsci.block.BlockAssembler;
import com.cooperativesolutionism.nmsci.block.BlockMessagePayloadFetcher;
import com.cooperativesolutionism.nmsci.block.SelectedBlockMessages;
import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.enumeration.MsgTypeEnum;
import com.cooperativesolutionism.nmsci.model.MsgAbstract;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyEmpowerMsgRepository;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeLockedMsgRepository;
import com.cooperativesolutionism.nmsci.repository.FlowNodeRegisterMsgRepository;
import com.cooperativesolutionism.nmsci.repository.MessagePayloadProjection;
import com.cooperativesolutionism.nmsci.repository.TransactionMountMsgRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * 多类型区块（含中心签名类型）验证：用生产侧 {@link ProtocolRawBytesBuilder#centralSignData} 与真实中心私钥
 * 忠实构造「公证」「交易记录」的落库字节，连同「注册」装配进一个真实区块，覆盖中心签名/确认时间戳偏移、
 * 双成员签名、交易 PoW、金额/币种以及有状态授权校验——无需 Docker。
 */
class MultiTypeBlockVerificationTest {

    private static final int EASY_NBITS = 0x20ffffff;

    private final ProtocolMessageBuilder builder = new ProtocolMessageBuilder();
    private final ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
    private final ChainVerifier verifier = new ChainVerifier();

    @BeforeAll
    static void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void verifiesBlockWithRegisterEmpowerAndTransactionRecord() {
        UUID registerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID empowerId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID recordId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        byte[] registerStored = builder.flowNodeRegister(registerId, TestKeyPairs.FLOW_NODE_A, EASY_NBITS);

        byte[] empowerInbound = builder.centralPubkeyEmpower(empowerId, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL);
        byte[] empowerStored = centrallySign(empowerInbound, 84, 1000L);

        byte[] recordInbound = builder.transactionRecord(
                recordId, 500L, TestKeyPairs.CONSUME_NODE_A, TestKeyPairs.FLOW_NODE_A, TestKeyPairs.CENTRAL, EASY_NBITS);
        byte[] recordStored = centrallySign(recordInbound, 135, 2000L);

        long maxMsgTimestamp = 2000L;

        BlockAssembler assembler = new BlockAssembler();
        FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
        CentralPubkeyEmpowerMsgRepository empowerRepository = mock(CentralPubkeyEmpowerMsgRepository.class);
        TransactionRecordMsgRepository recordRepository = mock(TransactionRecordMsgRepository.class);
        lenient().when(registerRepository.findPayloadByIdIn(List.of(registerId)))
                .thenReturn(List.of(projection(registerId, registerStored)));
        lenient().when(empowerRepository.findPayloadByIdIn(List.of(empowerId)))
                .thenReturn(List.of(projection(empowerId, empowerStored)));
        lenient().when(recordRepository.findPayloadByIdIn(List.of(recordId)))
                .thenReturn(List.of(projection(recordId, recordStored)));

        ReflectionTestUtils.setField(assembler, "nmsciProperties", properties());
        ReflectionTestUtils.setField(assembler, "blockMessagePayloadFetcher", new BlockMessagePayloadFetcher(
                registerRepository,
                empowerRepository,
                mock(CentralPubkeyLockedMsgRepository.class),
                mock(FlowNodeLockedMsgRepository.class),
                recordRepository,
                mock(TransactionMountMsgRepository.class)
        ));

        Map<MsgTypeEnum, List<MsgAbstract>> messagesByType = new LinkedHashMap<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            messagesByType.put(msgType, new ArrayList<>());
        }
        messagesByType.get(MsgTypeEnum.FlowNodeRegisterMsg).add(msgAbstract(registerId, MsgTypeEnum.FlowNodeRegisterMsg, 0L));
        messagesByType.get(MsgTypeEnum.CentralPubkeyEmpowerMsg).add(msgAbstract(empowerId, MsgTypeEnum.CentralPubkeyEmpowerMsg, 1000L));
        messagesByType.get(MsgTypeEnum.TransactionRecordMsg).add(msgAbstract(recordId, MsgTypeEnum.TransactionRecordMsg, 2000L));
        SelectedBlockMessages selected = new SelectedBlockMessages(messagesByType, maxMsgTimestamp);

        AssembledBlock block = assembler.assemble(null, selected);

        List<ParsedBlock> blocks = DatBlockReader.readConcatenated(block.getDatBytes(), "blk00000000.dat");
        assertEquals(1, blocks.size());
        assertEquals(3, blocks.get(0).messages().size());

        ChainVerificationResult result = verifier.verify(blocks, VerifierOptions.builder()
                .expectedCentralPubkey(TestKeyPairs.CENTRAL.pubkey())
                .includeStatefulReplay(true)
                .build());

        assertTrue(result.ok(), () -> "期望多类型区块验证通过，但有失败:\n" + result.render());
    }

    /** 忠实复刻 CentralSignatureService：stored = verifyData || 各成员签名 || 时间戳(8) || 中心签名(64)。 */
    private byte[] centrallySign(byte[] inbound, int verifyDataSize, long timestamp) {
        byte[] verifyData = Arrays.copyOfRange(inbound, 0, verifyDataSize);
        byte[] signaturesBlob = Arrays.copyOfRange(inbound, verifyDataSize, inbound.length);
        int signatureCount = signaturesBlob.length / 64;
        byte[][] memberSignatures = new byte[signatureCount][];
        for (int i = 0; i < signatureCount; i++) {
            memberSignatures[i] = Arrays.copyOfRange(signaturesBlob, i * 64, i * 64 + 64);
        }
        byte[] centralSignData = rawBytesBuilder.centralSignData(verifyData, timestamp, memberSignatures);
        byte[] centralSignature;
        try {
            centralSignature = Secp256k1EncryptUtil.derToRs(Secp256k1EncryptUtil.signData(
                    centralSignData, Secp256k1EncryptUtil.rawToPrivateKey(TestKeyPairs.CENTRAL.prikey())));
        } catch (Exception e) {
            throw new IllegalStateException("测试中心签名失败", e);
        }
        ByteArrayOutputStream stored = new ByteArrayOutputStream();
        stored.writeBytes(centralSignData);
        stored.writeBytes(centralSignature);
        return stored.toByteArray();
    }

    private static MsgAbstract msgAbstract(UUID id, MsgTypeEnum type, long confirmTimestamp) {
        MsgAbstract msgAbstract = new MsgAbstract();
        msgAbstract.setId(new byte[]{(byte) type.getValue()});
        msgAbstract.setMsgId(id);
        msgAbstract.setMsgType(type.getValue());
        msgAbstract.setConfirmTimestamp(confirmTimestamp);
        msgAbstract.setIsInBlock(false);
        return msgAbstract;
    }

    private static MessagePayloadProjection projection(UUID id, byte[] rawBytes) {
        byte[] txid = MerkleTreeUtil.calcTxid(rawBytes);
        return new MessagePayloadProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public byte[] getRawBytes() {
                return rawBytes;
            }

            @Override
            public byte[] getTxid() {
                return txid;
            }
        };
    }

    private static NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        properties.setBlockVersion(1);
        properties.setBlockHeaderSize(229);
        properties.setSourceCodeZipHash("0000000000000000000000000000000000000000000000000000000000000000");
        properties.setRegisterDifficultyTargetNbits(EASY_NBITS);
        properties.setTransactionDifficultyTargetNbits(EASY_NBITS);
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }
}
