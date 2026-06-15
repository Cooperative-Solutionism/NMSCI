package com.cooperativesolutionism.nmsci.service;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import com.cooperativesolutionism.nmsci.converter.CentralPubkeyLockedMsgConverter;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.model.CentrallySignedMessage;
import com.cooperativesolutionism.nmsci.protocol.CentralSignatureService;
import com.cooperativesolutionism.nmsci.protocol.ProtocolRawBytesBuilder;
import com.cooperativesolutionism.nmsci.protocol.SignatureValidator;
import com.cooperativesolutionism.nmsci.repository.CentralPubkeyLockedMsgRepository;
import com.cooperativesolutionism.nmsci.support.ProtocolMessageBuilder;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CentralPubkeyLockedMsgServiceTest {

    private final ProtocolMessageBuilder messageBuilder = new ProtocolMessageBuilder();

    @Test
    void savesLockMessageDrainsBlocksAndRequestsShutdownWithoutSystemExit() {
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyLocked(
                        UUID.fromString("88888888-8888-8888-8888-888888888888"),
                        TestKeyPairs.CENTRAL
                )
        );

        CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
        when(repository.existsById(msg.getId())).thenReturn(false);
        when(repository.existsByCentralPubkey(msg.getCentralPubkey())).thenReturn(false);
        when(repository.save(msg)).thenReturn(msg);

        MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
        BlockChainService blockChainService = mock(BlockChainService.class);
        CentralPubkeyLockShutdownService shutdownService = mock(CentralPubkeyLockShutdownService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
        CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService();
        ReflectionTestUtils.setField(service, "nmsciProperties", properties());
        ReflectionTestUtils.setField(service, "centralPubkeyLockedMsgRepository", repository);
        ReflectionTestUtils.setField(service, "msgAbstractService", msgAbstractService);
        ReflectionTestUtils.setField(service, "blockChainService", blockChainService);
        ReflectionTestUtils.setField(service, "signatureValidator", new SignatureValidator());
        ReflectionTestUtils.setField(service, "protocolRawBytesBuilder", rawBytesBuilder);
        ReflectionTestUtils.setField(service, "centralSignatureService", new CentralSignatureService(properties(), rawBytesBuilder));
        ReflectionTestUtils.setField(service, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(service, "shutdownService", shutdownService);

        service.saveCentralPubkeyLockedMsg(msg);

        assertNotNull(msg.getConfirmTimestamp());
        assertEquals(64, msg.getCentralSignature().length);
        assertEquals(187, msg.getRawBytes().length);
        assertEquals(32, msg.getTxid().length);
        verify(repository).save(msg);
        verify(msgAbstractService).saveMsgAbstract(msg);
        verify(blockChainService).generateBlockUntilNoNotInBlockMsgs();
        verify(shutdownService).requestShutdown();
    }

    @Test
    void centralPubkeyLockedMsgUsesCentrallySignedMessageContract() {
        assertInstanceOf(CentrallySignedMessage.class, new CentralPubkeyLockedMsg());
    }

    private NmsciProperties properties() {
        NmsciProperties properties = new NmsciProperties();
        NmsciProperties.CentralKeyPair centralKeyPair = new NmsciProperties.CentralKeyPair();
        centralKeyPair.setPubkey(TestKeyPairs.CENTRAL.pubkeyBase64());
        centralKeyPair.setPrikey(TestKeyPairs.CENTRAL.prikeyBase64());
        properties.setCentralKeyPair(centralKeyPair);
        return properties;
    }
}
