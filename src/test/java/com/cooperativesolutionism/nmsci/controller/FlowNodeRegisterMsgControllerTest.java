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
import org.springframework.util.StopWatch;

import java.math.BigInteger;
import java.security.Security;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@AutoConfigureMockMvc
@SpringBootTest(classes = NmsciApplication.class)
class FlowNodeRegisterMsgControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${register-difficulty-target-nbits}")
    private int registerDifficultyTargetNbits;

    @Test
    void saveFlowNodeRegisterMsg() {
        Security.addProvider(new BouncyCastleProvider());
        StopWatch stopWatch = new StopWatch("流转节点注册测试");

        byte[] testData;
        byte[] verfyData;

        short msgType = 0;
        UUID uuid = UUID.randomUUID();
        BigInteger registerDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));
        byte[] flowNodePubkey = ByteArrayUtil.base64ToBytes("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
        byte[] flowNodePrikey = ByteArrayUtil.base64ToBytes("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");

        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));

        stopWatch.start("计算Pow数据");
        byte[] mineData;
        while (true) {
            int nonce = (int) (Math.random() * Integer.MAX_VALUE);
            mineData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(nonce));
            mineData = ArrayUtils.addAll(mineData, flowNodePubkey);
            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
            if (mineDataHash.compareTo(registerDifficultyTarget) < 0) {
                System.out.println("registerDifficultyTargetHex = " + String.format("%064x", registerDifficultyTarget));
                System.out.println("mineDataHashHex = " + ByteArrayUtil.bytesToHex(Sha256Util.doubleDigest(mineData)));
                System.out.println("nonce = " + nonce);
                verfyData = mineData;
                break;
            }
        }
        stopWatch.stop();
        System.out.println(stopWatch.prettyPrint());

        try {
            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
            );
            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));

            mockMvc.perform(post("/flow-node-register-msg/send")
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

    @Test
    void saveFlowNodeRegisterMsgUseData() {
        Security.addProvider(new BouncyCastleProvider());
        StopWatch stopWatch = new StopWatch("流转节点注册测试");

        byte[] testData;
        byte[] verfyData;

        short msgType = 0;
        UUID uuid = UUID.fromString("29d9c0c2-163c-436e-91c7-2967915e67f0");
        BigInteger registerDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(0x20ffffff));
        byte[] flowNodePubkey = ByteArrayUtil.hexToBytes("032d9668353fb153d9cbc79fd28eafb2f5fdf4d5e81c330cfb44650feabe571273");
        byte[] flowNodePrikey = ByteArrayUtil.hexToBytes("8ddbc16da570b3f54771c9039d51496aa2bc5d8198381a67a8e6453bc4f61d4a");

        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));

        stopWatch.start("计算Pow数据");
        byte[] mineData;
        while (true) {
            int nonce = 0;
            mineData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(nonce));
            mineData = ArrayUtils.addAll(mineData, flowNodePubkey);
            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
            if (mineDataHash.compareTo(registerDifficultyTarget) < 0) {
                System.out.println("registerDifficultyTargetHex = " + String.format("%064x", registerDifficultyTarget));
                System.out.println("mineDataHashHex = " + ByteArrayUtil.bytesToHex(Sha256Util.doubleDigest(mineData)));
                System.out.println("nonce = " + nonce);
                verfyData = mineData;
                break;
            }
        }
        stopWatch.stop();
        System.out.println(stopWatch.prettyPrint());
        System.out.println("ByteArrayUtil.bytesToHex(verfyData) = " + ByteArrayUtil.bytesToHex(verfyData));
//        000029d9c0c2163c436e91c72967915e67f020ffffff00000000032d9668353fb153d9cbc79fd28eafb2f5fdf4d5e81c330cfb44650feabe571273
        try {
            //client dblsha256
//            61d3496a78be856d5564463b61ec1f96fb33bee869a52d12568dc5ccd61e513c
            //server dblsha256
//            61d3496a78be856d5564463b61ec1f96fb33bee869a52d12568dc5ccd61e513c

            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(
                    verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
            );

            System.out.println("ByteArrayUtil.bytesToHex(flowNodeSign) = " + ByteArrayUtil.bytesToHex(flowNodeSign));
            System.out.println("ByteArrayUtil.bytesToHex(flowNodeSign)rs = " + ByteArrayUtil.bytesToHex(Secp256k1EncryptUtil.derToRs(flowNodeSign)));
            System.out.println("Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey) = " + Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey));
            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));
//            000029d9c0c2163c436e91c72967915e67f020ffffff00000000032d9668353fb153d9cbc79fd28eafb2f5fdf4d5e81c330cfb44650feabe57127382d877c726eafc971ef0df660de05229058f291584d7e1d2f5cd88f61ad794833536db3ee94c23ceaa2ffff21226369d96764867f3e11c3cf91d85b858250c97
//            000029d9c0c2163c436e91c72967915e67f020ffffff00000000032d9668353fb153d9cbc79fd28eafb2f5fdf4d5e81c330cfb44650feabe5712734d2f670ecf5662fe0e95b47f2f6d5d518c203c951f7ef115e80d46f98188a95d47a64e52e58cb863b5cd22713838ed3682e0849d6f5b313e166145a070f6c684
            System.out.println("testData = " + ByteArrayUtil.bytesToHex(testData));

            mockMvc.perform(post("/flow-node-register-msg/send")
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