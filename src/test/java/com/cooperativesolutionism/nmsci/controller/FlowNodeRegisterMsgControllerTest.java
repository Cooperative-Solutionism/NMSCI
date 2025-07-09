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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StopWatch;

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
class FlowNodeRegisterMsgControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${register-difficulty-target-nbits}")
    private int registerDifficultyTargetNbits;

    @Test
    void saveFlowNodeRegisterMsg() {
        Security.addProvider(new BouncyCastleProvider());
        StopWatch stopWatch = new StopWatch("流转节点注册测试");

        byte[] testData;
        byte[] verfyData;

        short msgType = 2;
        UUID uuid = UUID.randomUUID();
        BigInteger registerDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));
        byte[] flowNodePubkey = ByteArrayUtil.base64ToBytes("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
        byte[] flowNodePrikey = ByteArrayUtil.base64ToBytes("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);

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
            mineData = ArrayUtils.addAll(mineData, centralPubkey);
            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
            if (mineDataHash.compareTo(registerDifficultyTarget) < 0) {
//                System.out.println("registerDifficultyTarget = " + registerDifficultyTarget);
//                System.out.println("mineDataHash = " + mineDataHash);
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
}