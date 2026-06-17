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
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.MerkleTreeUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerifyChainCliTest {

    private static final int EASY_NBITS = 0x20ffffff;

    @BeforeAll
    static void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void parsesDefaults() {
        VerifyChainCli.Options options = VerifyChainCli.parse(new String[]{});
        assertEquals(Path.of("file", "dat"), options.datDir);
        assertTrue(options.stateful);
        assertFalse(options.help);
    }

    @Test
    void parsesPositionalDatDirAndFlags() {
        VerifyChainCli.Options options = VerifyChainCli.parse(new String[]{"some/dir", "--no-stateful"});
        assertEquals(Path.of("some/dir"), options.datDir);
        assertFalse(options.stateful);
    }

    @Test
    void parsesCentralPubkeyAsHexOrBase64() {
        String hex = ByteArrayUtil.bytesToHex(TestKeyPairs.CENTRAL.pubkey());
        assertArrayEquals(TestKeyPairs.CENTRAL.pubkey(),
                VerifyChainCli.parse(new String[]{"--central-pubkey", hex}).expectedCentralPubkey);
        assertArrayEquals(TestKeyPairs.CENTRAL.pubkey(),
                VerifyChainCli.parse(new String[]{"--central-pubkey", TestKeyPairs.CENTRAL.pubkeyBase64()}).expectedCentralPubkey);
    }

    @Test
    void parsesSourceHashAndStartingPrevHash() {
        String hex64 = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
        VerifyChainCli.Options options = VerifyChainCli.parse(new String[]{
                "--source-hash", hex64, "--starting-prev-hash", hex64});
        assertEquals(hex64, options.expectedSourceHashHex);
        assertArrayEquals(ByteArrayUtil.hexToBytes(hex64), options.startingPreviousHash);
    }

    @Test
    void rejectsUnknownOptionAndMissingValue() {
        assertThrows(RuntimeException.class, () -> VerifyChainCli.parse(new String[]{"--bogus"}));
        assertThrows(RuntimeException.class, () -> VerifyChainCli.parse(new String[]{"--central-pubkey"}));
    }

    @Test
    void runReportsValidForGoodChain(@TempDir Path datDir) throws IOException {
        Files.write(datDir.resolve("blk00000000.dat"), goodRegisterDat());

        VerifyChainCli.Options options = new VerifyChainCli.Options();
        options.datDir = datDir;
        options.expectedCentralPubkey = TestKeyPairs.CENTRAL.pubkey();

        ChainVerificationResult result = VerifyChainCli.run(options);
        assertTrue(result.ok(), () -> result.render());
    }

    @Test
    void runReportsInvalidForCorruptedChain(@TempDir Path datDir) throws IOException {
        byte[] datBytes = goodRegisterDat();
        datBytes[12 + 76] ^= 0x01; // 篡改默克尔根
        Files.write(datDir.resolve("blk00000000.dat"), datBytes);

        VerifyChainCli.Options options = new VerifyChainCli.Options();
        options.datDir = datDir;

        ChainVerificationResult result = VerifyChainCli.run(options);
        assertFalse(result.ok());
    }

    @Test
    void runReportsValidForEmptyDirectory(@TempDir Path datDir) {
        VerifyChainCli.Options options = new VerifyChainCli.Options();
        options.datDir = datDir;
        ChainVerificationResult result = VerifyChainCli.run(options);
        assertTrue(result.ok());
        assertEquals(0, result.totalBlocks());
    }

    private static byte[] goodRegisterDat() {
        UUID msgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        byte[] rawBytes = new ProtocolMessageBuilder().flowNodeRegister(msgId, TestKeyPairs.FLOW_NODE_A, EASY_NBITS);
        byte[] txid = MerkleTreeUtil.calcTxid(rawBytes);

        FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
        when(registerRepository.findPayloadByIdIn(List.of(msgId)))
                .thenReturn(List.of(new MessagePayloadProjection() {
                    @Override
                    public UUID getId() {
                        return msgId;
                    }

                    @Override
                    public byte[] getRawBytes() {
                        return rawBytes;
                    }

                    @Override
                    public byte[] getTxid() {
                        return txid;
                    }
                }));

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

        BlockAssembler assembler = new BlockAssembler(properties, new BlockMessagePayloadFetcher(
                registerRepository,
                mock(CentralPubkeyEmpowerMsgRepository.class),
                mock(CentralPubkeyLockedMsgRepository.class),
                mock(FlowNodeLockedMsgRepository.class),
                mock(TransactionRecordMsgRepository.class),
                mock(TransactionMountMsgRepository.class)
        ));

        Map<MsgTypeEnum, List<MsgAbstract>> messagesByType = new LinkedHashMap<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            messagesByType.put(msgType, new ArrayList<>());
        }
        MsgAbstract msgAbstract = new MsgAbstract();
        msgAbstract.setId(new byte[]{1});
        msgAbstract.setMsgId(msgId);
        msgAbstract.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msgAbstract.setConfirmTimestamp(0L);
        msgAbstract.setIsInBlock(false);
        messagesByType.get(MsgTypeEnum.FlowNodeRegisterMsg).add(msgAbstract);

        AssembledBlock block = assembler.assemble(null, new SelectedBlockMessages(messagesByType, 0L));
        return block.getDatBytes();
    }
}
