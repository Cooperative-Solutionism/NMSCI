package com.cooperativesolutionism.nmsci.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约：消息类响应不得输出 rawBytes（原始字节缓存），与 docs/API.md §1.4/§10.2 一致。
 * 防止某个消息实体回退到序列化 rawBytes，导致泄露原始字节并使响应体翻倍。
 */
class MessageRawBytesJsonIgnoreTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void messageEntitiesDoNotSerializeRawBytes() throws Exception {
        List<Message> messages = List.of(
                new TransactionRecordMsg(),
                new TransactionMountMsg(),
                new FlowNodeRegisterMsg(),
                new FlowNodeLockedMsg(),
                new CentralPubkeyEmpowerMsg(),
                new CentralPubkeyLockedMsg()
        );

        for (Message message : messages) {
            message.setRawBytes(new byte[]{1, 2, 3, 4});
            message.setTxid(new byte[]{10, 11, 12, 13});

            String json = objectMapper.writeValueAsString(message);

            assertFalse(
                    json.contains("rawBytes"),
                    () -> message.getClass().getSimpleName() + " 响应不应包含 rawBytes 字段：" + json
            );
            assertTrue(
                    json.contains("txid"),
                    () -> message.getClass().getSimpleName() + " 响应应仍输出其它字节字段（txid）：" + json
            );
        }
    }
}
