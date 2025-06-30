//package com.cooperativesolutionism.nmsci;
//
//import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
//import com.cooperativesolutionism.nmsci.util.PoWUtil;
//import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
//import com.cooperativesolutionism.nmsci.util.Sha256Util;
//import org.apache.commons.lang3.ArrayUtils;
//import org.bouncycastle.jce.provider.BouncyCastleProvider;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.math.BigInteger;
//import java.security.Security;
//import java.util.Arrays;
//import java.util.Base64;
//import java.util.UUID;
//
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@AutoConfigureMockMvc
//@SpringBootTest
//class NmsciApplicationTests {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Value("${central-key-pair.pubkey}")
//    private String centralPubkeyBase64;
//
//    @Value("${central-key-pair.prikey}")
//    private String centralPrikeyBase64;
//
//    @Value("${register-difficulty-target-nbits}")
//    private int registerDifficultyTargetNbits;
//
//    @Value("${transaction-difficulty-target-nbits}")
//    private int transactionDifficultyTargetNbits;
//
//    @Test
//    void centralPubkeyEmpowerMsgControllerTest() {
//        Security.addProvider(new BouncyCastleProvider());
//
//        byte[] testData;
//        byte[] verfyData;
//
//        short msgType = 0;
//        UUID uuid = UUID.randomUUID();
//        byte[] flowNodePubkey = Base64.getDecoder().decode("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
//        byte[] flowNodePrikey = Base64.getDecoder().decode("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
//        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
//
//        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
//                ByteArrayUtil.uuidToBytes(uuid)
//        );
//        verfyData = ArrayUtils.addAll(verfyData, flowNodePubkey);
//        verfyData = ArrayUtils.addAll(verfyData, centralPubkey);
//
//        try {
//            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
//            );
//            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));
//
//            mockMvc.perform(post("/central-pubkey-empower-msg/send")
//                            .contentType("application/octet-stream")
//                            .content(testData)
//                    )
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.code").value(200))
//                    .andExpect(result -> {
//                        String response = result.getResponse().getContentAsString();
//                        System.out.println("Response: " + response);
//                    });
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Test
//    void centralPubkeyLockedMsgControllerTest() {
//        Security.addProvider(new BouncyCastleProvider());
//
//        byte[] testData;
//        byte[] verfyData;
//
//        short msgType = 1;
//        UUID uuid = UUID.randomUUID();
//        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
//        byte[] centralPrikey = Base64.getDecoder().decode(centralPrikeyBase64);
//
//        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
//                ByteArrayUtil.uuidToBytes(uuid)
//        );
//        verfyData = ArrayUtils.addAll(verfyData, centralPubkey);
//
//        try {
//            byte[] centralSignPre = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey)
//            );
//            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(centralSignPre));
//
//            mockMvc.perform(post("/central-pubkey-locked-msg/send")
//                            .contentType("application/octet-stream")
//                            .content(testData)
//                    )
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.code").value(200))
//                    .andExpect(result -> {
//                        String response = result.getResponse().getContentAsString();
//                        System.out.println("Response: " + response);
//                    });
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Test
//    void flowNodeRegisterMsgControllerTest() {
//        Security.addProvider(new BouncyCastleProvider());
//
//        byte[] testData;
//        byte[] verfyData;
//
//        short msgType = 2;
//        UUID uuid = UUID.randomUUID();
//        BigInteger registerDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));
//        byte[] flowNodePubkey = Base64.getDecoder().decode("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
//        byte[] flowNodePrikey = Base64.getDecoder().decode("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
//        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
//
//        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
//                ByteArrayUtil.uuidToBytes(uuid)
//        );
//        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));
//
//        byte[] mineData;
//        while (true) {
//            int nonce = (int) (Math.random() * Integer.MAX_VALUE);
//            mineData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(nonce));
//            mineData = ArrayUtils.addAll(mineData, flowNodePubkey);
//            mineData = ArrayUtils.addAll(mineData, centralPubkey);
//            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
//            if (mineDataHash.compareTo(registerDifficultyTarget) < 0) {
//                System.out.println("registerDifficultyTarget = " + registerDifficultyTarget);
//                System.out.println("mineDataHash = " + mineDataHash);
//                System.out.println("nonce = " + nonce);
//                verfyData = mineData;
//                break;
//            }
//        }
//
//        System.out.println("mineData = " + Arrays.toString(mineData));
//        try {
//            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
//            );
//            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));
//
//            mockMvc.perform(post("/flow-node-register-msg/send")
//                            .contentType("application/octet-stream")
//                            .content(testData)
//                    )
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.code").value(200))
//                    .andExpect(result -> {
//                        String response = result.getResponse().getContentAsString();
//                        System.out.println("Response: " + response);
//                    });
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Test
//    void flowNodeLockedMsgControllerTest() {
//        Security.addProvider(new BouncyCastleProvider());
//
//        byte[] testData;
//        byte[] verfyData;
//
//        short msgType = 3;
//        UUID uuid = UUID.randomUUID();
//        byte[] flowNodePubkey = Base64.getDecoder().decode("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
//        byte[] flowNodePrikey = Base64.getDecoder().decode("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
//        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
//
//        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
//                ByteArrayUtil.uuidToBytes(uuid)
//        );
//        verfyData = ArrayUtils.addAll(verfyData, flowNodePubkey);
//        verfyData = ArrayUtils.addAll(verfyData, centralPubkey);
//
//        try {
//            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
//            );
//            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));
//
//            mockMvc.perform(post("/flow-node-locked-msg/send")
//                            .contentType("application/octet-stream")
//                            .content(testData)
//                    )
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.code").value(200))
//                    .andExpect(result -> {
//                        String response = result.getResponse().getContentAsString();
//                        System.out.println("Response: " + response);
//                    });
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Test
//    void transactionRecordMsgControllerTest() {
//        Security.addProvider(new BouncyCastleProvider());
//
//        byte[] testData;
//        byte[] verfyData;
//
//        short msgType = 4;
//        UUID uuid = UUID.randomUUID();
//        long amount = 1000000L;
//        short currencyType = 2;
//        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
//        byte[] consumeNodePubkey = Base64.getDecoder().decode("A7Cn5AqooeaoG59YPCCP7lG+wTggKf0/6wjc8LtaGFFz");
//        byte[] consumeNodePrikey = Base64.getDecoder().decode("cvjs/qSKitGfNP+elnBRjJr9fSKn4iI1C5bsTH7t948=");
//        byte[] flowNodePubkey = Base64.getDecoder().decode("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
//        byte[] flowNodePrikey = Base64.getDecoder().decode("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
//        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
//
//        // 【信息类型2字节(4)】+【uuid16字节】+【金额8字节】+【货币类型2字节】+【交易难度目标4字节】+【随机数4字节】+【消费节点公钥33字节】+【流转节点公钥33字节】+【中心公钥33字节】
//        // +【消费节点对信息(前9项数据)签名64字节】+【流转节点对信息(*前9项数据，也是前9项，方便两者同时签名)签名64字节】
//        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
//                ByteArrayUtil.uuidToBytes(uuid)
//        );
//        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.longToBytes(amount));
//        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.shortToBytes(currencyType));
//        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
//
//        byte[] mineData;
//        while (true) {
//            int nonce = (int) (Math.random() * Integer.MAX_VALUE);
//            mineData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(nonce));
//            mineData = ArrayUtils.addAll(mineData, consumeNodePubkey);
//            mineData = ArrayUtils.addAll(mineData, flowNodePubkey);
//            mineData = ArrayUtils.addAll(mineData, centralPubkey);
//            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
//            if (mineDataHash.compareTo(transactionDifficultyTarget) < 0) {
//                System.out.println("transactionDifficultyTarget = " + transactionDifficultyTarget);
//                System.out.println("mineDataHash = " + mineDataHash);
//                System.out.println("nonce = " + nonce);
//                verfyData = mineData;
//                break;
//            }
//        }
//
//        System.out.println("mineData = " + Arrays.toString(mineData));
//        try {
//            byte[] consumeNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(consumeNodePrikey)
//            );
//            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
//            );
//            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(consumeNodeSign));
//            testData = ArrayUtils.addAll(testData, Secp256k1EncryptUtil.derToRs(flowNodeSign));
//
//            mockMvc.perform(post("/transaction-record-msg/send")
//                            .contentType("application/octet-stream")
//                            .content(testData)
//                    )
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.code").value(200))
//                    .andExpect(result -> {
//                        String response = result.getResponse().getContentAsString();
//                        System.out.println("Response: " + response);
//                    });
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    @Test
//    void transactionMountMsgControllerTest() {
//        Security.addProvider(new BouncyCastleProvider());
//
//        byte[] testData;
//        byte[] verfyData;
//
//        short msgType = 5;
//        UUID uuid = UUID.randomUUID();
//        UUID recordUuid = UUID.fromString("4079f6ee-7469-4742-bb64-16477634918b");
//        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
//        byte[] consumeNodePubkey = Base64.getDecoder().decode("A7Cn5AqooeaoG59YPCCP7lG+wTggKf0/6wjc8LtaGFFz");
//        byte[] consumeNodePrikey = Base64.getDecoder().decode("cvjs/qSKitGfNP+elnBRjJr9fSKn4iI1C5bsTH7t948=");
//        byte[] flowNodePubkey = Base64.getDecoder().decode("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
//        byte[] flowNodePrikey = Base64.getDecoder().decode("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
//        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);
//
//        // 【信息类型2字节(5)】+【uuid16字节】+【挂载的交易记录信息的uuid16字节】+【交易难度目标4字节】+【随机数4字节】+【挂载的交易信息的消费节点公钥33字节】+【挂载的流转节点公钥33字节】+【中心公钥33字节】
//        // +【消费节点对信息(前8项数据)签名64字节】+【挂载的生产者账号对信息(*前8项数据，也是前8项，方便两者同时签名)签名64字节】
//        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
//                ByteArrayUtil.uuidToBytes(uuid)
//        );
//        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.uuidToBytes(recordUuid));
//        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));
//
//        byte[] mineData;
//        while (true) {
//            int nonce = (int) (Math.random() * Integer.MAX_VALUE);
//            mineData = ArrayUtils.addAll(verfyData, ByteArrayUtil.intToBytes(nonce));
//            mineData = ArrayUtils.addAll(mineData, consumeNodePubkey);
//            mineData = ArrayUtils.addAll(mineData, flowNodePubkey);
//            mineData = ArrayUtils.addAll(mineData, centralPubkey);
//            BigInteger mineDataHash = new BigInteger(1, Sha256Util.doubleDigest(mineData));
//            if (mineDataHash.compareTo(transactionDifficultyTarget) < 0) {
//                System.out.println("transactionDifficultyTarget = " + transactionDifficultyTarget);
//                System.out.println("mineDataHash = " + mineDataHash);
//                System.out.println("nonce = " + nonce);
//                verfyData = mineData;
//                break;
//            }
//        }
//
//        System.out.println("mineData = " + Arrays.toString(mineData));
//        try {
//            byte[] consumeNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(consumeNodePrikey)
//            );
//            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
//                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
//            );
//            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(consumeNodeSign));
//            testData = ArrayUtils.addAll(testData, Secp256k1EncryptUtil.derToRs(flowNodeSign));
//
//            mockMvc.perform(post("/transaction-mount-msg/send")
//                            .contentType("application/octet-stream")
//                            .content(testData)
//                    )
//                    .andExpect(status().isOk())
//                    .andExpect(jsonPath("$.code").value(200))
//                    .andExpect(result -> {
//                        String response = result.getResponse().getContentAsString();
//                        System.out.println("Response: " + response);
//                    });
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//}
