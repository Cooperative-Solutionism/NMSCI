package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionRecordMsgControllerEncodingTest {

    @Test
    void searchDecodesPubkeyQueryParametersAsHex() {
        TransactionRecordMsgController controller = new TransactionRecordMsgController();
        TransactionRecordMsgService service = mock(TransactionRecordMsgService.class);
        ReflectionTestUtils.setField(controller, "transactionRecordMsgService", service);
        when(service.searchTransactionRecordMsgs(
                any(), any(), any(), any(), any(), any(Pageable.class)
        )).thenReturn(new SliceImpl<TransactionRecordMsg>(List.of()));

        controller.searchTransactionRecordMsgs(
                ByteArrayUtil.bytesToHex(TestKeyPairs.CONSUME_NODE_A.pubkey()),
                ByteArrayUtil.bytesToHex(TestKeyPairs.FLOW_NODE_A.pubkey()),
                null,
                null,
                null,
                0,
                50
        );

        ArgumentCaptor<byte[]> consumeNodePubkeyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> flowNodePubkeyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(service).searchTransactionRecordMsgs(
                consumeNodePubkeyCaptor.capture(),
                flowNodePubkeyCaptor.capture(),
                any(),
                any(),
                any(),
                any(Pageable.class)
        );
        assertArrayEquals(TestKeyPairs.CONSUME_NODE_A.pubkey(), consumeNodePubkeyCaptor.getValue());
        assertArrayEquals(TestKeyPairs.FLOW_NODE_A.pubkey(), flowNodePubkeyCaptor.getValue());
    }
}
