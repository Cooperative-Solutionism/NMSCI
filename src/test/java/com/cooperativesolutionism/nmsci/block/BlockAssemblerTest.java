package com.cooperativesolutionism.nmsci.block;

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
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockAssemblerTest {

    @BeforeAll
    static void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Test
    void assemblesBlockFromPayloadProjectionsWithoutLoadingFullEntities() {
        FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
        BlockAssembler assembler = newAssembler(registerRepository);

        UUID msgId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        byte[] rawBytes = new byte[123];
        rawBytes[0] = 7;
        byte[] txid = Sha256Util.doubleDigest(rawBytes);
        when(registerRepository.findPayloadByIdIn(List.of(msgId)))
                .thenReturn(List.of(new TestMessagePayloadProjection(msgId, rawBytes, txid)));

        AssembledBlock block = assembler.assemble(null, selected(msgId));

        assertEquals(block.getBlockInfo().getRawBytes().length + 12, block.getDatBytes().length);
        assertArrayEquals(block.getBlockInfo().getRawBytes(), bytesAfterDatHeader(block.getDatBytes()));
        assertFalse(block.getSelectedMsgAbstracts().get(0).getIsInBlock());
        verify(registerRepository).findPayloadByIdIn(List.of(msgId));
        verify(registerRepository, never()).findAllById(any());
    }

    @Test
    void leavesSelectedMessagesUnmarkedWhenPayloadLookupFails() {
        FlowNodeRegisterMsgRepository registerRepository = mock(FlowNodeRegisterMsgRepository.class);
        BlockAssembler assembler = newAssembler(registerRepository);

        UUID msgId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(registerRepository.findPayloadByIdIn(List.of(msgId))).thenReturn(List.of());
        SelectedBlockMessages selectedMessages = selected(msgId);
        MsgAbstract msgAbstract = selectedMessages.getMessagesByType().get(MsgTypeEnum.FlowNodeRegisterMsg).get(0);

        assertThrows(IllegalStateException.class, () -> assembler.assemble(null, selectedMessages));

        assertFalse(msgAbstract.getIsInBlock());
    }

    private static BlockAssembler newAssembler(FlowNodeRegisterMsgRepository registerRepository) {
        return new BlockAssembler(
                properties(),
                new BlockMessagePayloadFetcher(
                        registerRepository,
                        mock(CentralPubkeyEmpowerMsgRepository.class),
                        mock(CentralPubkeyLockedMsgRepository.class),
                        mock(FlowNodeLockedMsgRepository.class),
                        mock(TransactionRecordMsgRepository.class),
                        mock(TransactionMountMsgRepository.class)
                )
        );
    }

    private static NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        properties.setBlockVersion(1);
        properties.setSourceCodeZipHash("0000000000000000000000000000000000000000000000000000000000000000");
        properties.setRegisterDifficultyTargetNbits(0x20ffffff);
        properties.setTransactionDifficultyTargetNbits(0x20ffffff);
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }

    private static SelectedBlockMessages selected(UUID msgId) {
        Map<MsgTypeEnum, List<MsgAbstract>> messagesByType = new LinkedHashMap<>();
        for (MsgTypeEnum msgType : MsgTypeEnum.values()) {
            messagesByType.put(msgType, new ArrayList<>());
        }

        MsgAbstract msgAbstract = new MsgAbstract();
        msgAbstract.setId(new byte[]{1});
        msgAbstract.setMsgId(msgId);
        msgAbstract.setMsgType(MsgTypeEnum.FlowNodeRegisterMsg.getValue());
        msgAbstract.setConfirmTimestamp(123L);
        msgAbstract.setIsInBlock(false);
        messagesByType.get(MsgTypeEnum.FlowNodeRegisterMsg).add(msgAbstract);
        return new SelectedBlockMessages(messagesByType, 123L);
    }

    private static byte[] bytesAfterDatHeader(byte[] datBytes) {
        byte[] rawBlockBytes = new byte[datBytes.length - 12];
        System.arraycopy(datBytes, 12, rawBlockBytes, 0, rawBlockBytes.length);
        return rawBlockBytes;
    }

    private record TestMessagePayloadProjection(UUID id, byte[] rawBytes, byte[] txid) implements MessagePayloadProjection {

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
    }
}
