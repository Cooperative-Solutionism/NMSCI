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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
        BlockChainService blockChainService = mock(BlockChainService.class);
        CentralPubkeyLockShutdownService shutdownService = mock(CentralPubkeyLockShutdownService.class);
        List<String> phases = new ArrayList<>();
        AtomicBoolean inTransactionCallback = new AtomicBoolean(false);
        doAnswer(invocation -> {
            assertTrue(inTransactionCallback.get(), "lock message must be saved inside TransactionTemplate callback");
            phases.add("repository.save");
            return msg;
        }).when(repository).save(msg);
        doAnswer(invocation -> {
            assertTrue(inTransactionCallback.get(), "msg_abstract must be saved inside TransactionTemplate callback");
            phases.add("msgAbstractService.saveMsgAbstract");
            return null;
        }).when(msgAbstractService).saveMsgAbstract(msg);
        doAnswer(invocation -> {
            assertFalse(inTransactionCallback.get(), "block draining must run after TransactionTemplate callback");
            phases.add("blockChainService.generateBlockUntilNoNotInBlockMsgs");
            return null;
        }).when(blockChainService).generateBlockUntilNoNotInBlockMsgs();
        doAnswer(invocation -> {
            assertFalse(inTransactionCallback.get(), "shutdown request must run after TransactionTemplate callback");
            phases.add("shutdownService.requestShutdown");
            return null;
        }).when(shutdownService).requestShutdown();
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            phases.add("transactionTemplate.callback.start");
            inTransactionCallback.set(true);
            action.accept(new SimpleTransactionStatus());
            inTransactionCallback.set(false);
            phases.add("transactionTemplate.callback.end");
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
        CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService(
                properties(),
                repository,
                new MessageWritePipeline(msgAbstractService),
                new SignatureValidator(),
                rawBytesBuilder,
                new CentralSignatureService(properties(), rawBytesBuilder),
                transactionTemplate,
                shutdownService,
                blockChainService
        );

        service.saveCentralPubkeyLockedMsg(msg);

        assertNotNull(msg.getConfirmTimestamp());
        assertEquals(64, msg.getCentralSignature().length);
        assertEquals(187, msg.getRawBytes().length);
        assertEquals(32, msg.getTxid().length);
        verify(repository).save(msg);
        verify(msgAbstractService).saveMsgAbstract(msg);
        verify(blockChainService).generateBlockUntilNoNotInBlockMsgs();
        verify(shutdownService).requestShutdown();
        assertEquals(
                List.of(
                        "transactionTemplate.callback.start",
                        "repository.save",
                        "msgAbstractService.saveMsgAbstract",
                        "transactionTemplate.callback.end",
                        "blockChainService.generateBlockUntilNoNotInBlockMsgs",
                        "shutdownService.requestShutdown"
                ),
                phases
        );
    }

    @Test
    void requestsShutdownAndRethrowsWhenDrainFailsAfterLockMessageCommitted() {
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyLocked(
                        UUID.fromString("99999999-9999-9999-9999-999999999999"),
                        TestKeyPairs.CENTRAL
                )
        );

        CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
        when(repository.existsById(msg.getId())).thenReturn(false);
        when(repository.existsByCentralPubkey(msg.getCentralPubkey())).thenReturn(false);

        MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
        BlockChainService blockChainService = mock(BlockChainService.class);
        CentralPubkeyLockShutdownService shutdownService = mock(CentralPubkeyLockShutdownService.class);
        RuntimeException drainFailure = new RuntimeException("drain failed");
        org.mockito.Mockito.doThrow(drainFailure)
                .when(blockChainService)
                .generateBlockUntilNoNotInBlockMsgs();

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
        CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService(
                properties(),
                repository,
                new MessageWritePipeline(msgAbstractService),
                new SignatureValidator(),
                rawBytesBuilder,
                new CentralSignatureService(properties(), rawBytesBuilder),
                transactionTemplate,
                shutdownService,
                blockChainService
        );

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.saveCentralPubkeyLockedMsg(msg));

        assertSame(drainFailure, thrown);
        verify(repository).save(msg);
        verify(msgAbstractService).saveMsgAbstract(msg);
        verify(blockChainService).generateBlockUntilNoNotInBlockMsgs();
        verify(shutdownService).requestShutdown();
    }

    @Test
    void rethrowsDrainFailureAndSuppressesShutdownFailureWhenBothFail() {
        CentralPubkeyLockedMsg msg = new CentralPubkeyLockedMsgConverter().fromByteArray(
                messageBuilder.centralPubkeyLocked(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                        TestKeyPairs.CENTRAL
                )
        );

        CentralPubkeyLockedMsgRepository repository = mock(CentralPubkeyLockedMsgRepository.class);
        when(repository.existsById(msg.getId())).thenReturn(false);
        when(repository.existsByCentralPubkey(msg.getCentralPubkey())).thenReturn(false);

        MsgAbstractService msgAbstractService = mock(MsgAbstractService.class);
        BlockChainService blockChainService = mock(BlockChainService.class);
        CentralPubkeyLockShutdownService shutdownService = mock(CentralPubkeyLockShutdownService.class);
        RuntimeException drainFailure = new RuntimeException("drain failed");
        RuntimeException shutdownFailure = new RuntimeException("shutdown failed");
        org.mockito.Mockito.doThrow(drainFailure)
                .when(blockChainService)
                .generateBlockUntilNoNotInBlockMsgs();
        org.mockito.Mockito.doThrow(shutdownFailure)
                .when(shutdownService)
                .requestShutdown();

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(new SimpleTransactionStatus());
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        ProtocolRawBytesBuilder rawBytesBuilder = new ProtocolRawBytesBuilder();
        CentralPubkeyLockedMsgService service = new CentralPubkeyLockedMsgService(
                properties(),
                repository,
                new MessageWritePipeline(msgAbstractService),
                new SignatureValidator(),
                rawBytesBuilder,
                new CentralSignatureService(properties(), rawBytesBuilder),
                transactionTemplate,
                shutdownService,
                blockChainService
        );

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.saveCentralPubkeyLockedMsg(msg));

        assertSame(drainFailure, thrown);
        assertTrue(List.of(thrown.getSuppressed()).contains(shutdownFailure));
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
