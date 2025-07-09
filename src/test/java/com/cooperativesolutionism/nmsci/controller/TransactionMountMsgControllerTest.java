package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.NmsciApplication;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PoWUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigInteger;
import java.security.Security;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@AutoConfigureMockMvc
@SpringBootTest(classes = NmsciApplication.class)
class TransactionMountMsgControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${transaction-difficulty-target-nbits}")
    private int transactionDifficultyTargetNbits;

    @Test
    void saveTransactionMountMsg() {
        Security.addProvider(new BouncyCastleProvider());

        byte[] testData;
        byte[] verfyData;

        short msgType = 5;
        UUID uuid = UUID.randomUUID();
        UUID recordUuid = UUID.fromString("6d64af3e-8f34-4847-a6f0-9e6b16079c2d");
        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
        byte[] consumeNodePubkey = ByteArrayUtil.base64ToBytes("A7Cn5AqooeaoG59YPCCP7lG+wTggKf0/6wjc8LtaGFFz");
        byte[] consumeNodePrikey = ByteArrayUtil.base64ToBytes("cvjs/qSKitGfNP+elnBRjJr9fSKn4iI1C5bsTH7t948=");
        byte[] flowNodePubkey = ByteArrayUtil.base64ToBytes("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
        byte[] flowNodePrikey = ByteArrayUtil.base64ToBytes("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);

        // 【信息类型2字节(5)】+【uuid16字节】+【挂载的交易记录信息的uuid16字节】+【交易难度目标4字节】+【随机数4字节】+【挂载的交易信息的消费节点公钥33字节】+【挂载的流转节点公钥33字节】+【中心公钥33字节】
        // +【消费节点对信息(前8项数据)签名64字节】+【挂载的生产者账号对信息(*前8项数据，也是前8项，方便两者同时签名)签名64字节】
        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.uuidToBytes(recordUuid));
        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));

        byte[] mineData;
        while (true) {
            int nonce = (int) (Math.random() * Integer.MAX_VALUE);
            mineData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(nonce));
            mineData = ArrayUtils.addAll(mineData, consumeNodePubkey);
            mineData = ArrayUtils.addAll(mineData, flowNodePubkey);
            mineData = ArrayUtils.addAll(mineData, centralPubkey);
            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
            if (mineDataHash.compareTo(transactionDifficultyTarget) < 0) {
                System.out.println("transactionDifficultyTarget = " + transactionDifficultyTarget);
                System.out.println("mineDataHash = " + mineDataHash);
                System.out.println("nonce = " + nonce);
                verfyData = mineData;
                break;
            }
        }

        try {
            byte[] consumeNodeSign = Secp256k1EncryptUtil.signData(verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(consumeNodePrikey)
            );
            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
            );
            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(consumeNodeSign));
            testData = ArrayUtils.addAll(testData, Secp256k1EncryptUtil.derToRs(flowNodeSign));

            mockMvc.perform(post("/transaction-mount-msg/send")
                            .contentType("application/octet-stream")
                            .content(testData)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        System.out.println("Response: " + response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}