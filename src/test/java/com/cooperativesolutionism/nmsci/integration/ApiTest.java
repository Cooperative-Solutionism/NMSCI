package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.NmsciApplication;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.PoWUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import com.cooperativesolutionism.nmsci.util.Sha256Util;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.StopWatch;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.Security;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@AutoConfigureMockMvc
@SpringBootTest(classes = NmsciApplication.class)
public class ApiTest {

    private static final Logger logger = LoggerFactory.getLogger(ApiTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Value("${central-key-pair.prikey}")
    private String centralPrikeyBase64;

    @Value("${register-difficulty-target-nbits}")
    private int registerDifficultyTargetNbits;

    @Value("${transaction-difficulty-target-nbits}")
    private int transactionDifficultyTargetNbits;

    /**
     * 测试交易挂载功能
     */
    @Test
    public void testTransactionMount() {
        Security.addProvider(new BouncyCastleProvider());

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        byte[][] flowNodeKeyPair = generateKeyPair();
        byte[] flowNodePubkey = flowNodeKeyPair[0];
        byte[] flowNodePrikey = flowNodeKeyPair[1];

        testFlowNodeRegisterApi(flowNodePubkey, flowNodePrikey, registerDifficultyTargetNbits);
        testCentralPubkeyEmpowerApi(flowNodePubkey, flowNodePrikey, centralPubkey);

        byte[][] consumeNodeKeyPair = generateKeyPair();
        byte[] consumeNodePubkey = consumeNodeKeyPair[0];
        byte[] consumeNodePrikey = consumeNodeKeyPair[1];

        UUID recordUuid = testTransactionRecordApi(
                consumeNodePubkey,
                consumeNodePrikey,
                flowNodePubkey,
                flowNodePrikey,
                centralPubkey,
                transactionDifficultyTargetNbits
        );

        testTransactionMountApi(
                recordUuid,
                consumeNodePubkey,
                consumeNodePrikey,
                flowNodePubkey,
                flowNodePrikey,
                centralPubkey,
                transactionDifficultyTargetNbits
        );

        getReturningFlowRate(
                flowNodePubkey,
                flowNodePubkey
        );
    }

    /**
     * 中心公钥冻结消息接口测试
     */
    @Test
    public void testCentralPubkeyLock() {
        Security.addProvider(new BouncyCastleProvider());

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);
        byte[] centralPrikey = ByteArrayUtil.base64ToBytes(centralPrikeyBase64);

        saveCentralPubkeyLockedMsg(centralPubkey, centralPrikey);
    }

    /**
     * 流转节点冻结消息接口测试
     */
    @Test
    public void testFlowNodeLock() {
        Security.addProvider(new BouncyCastleProvider());

        byte[][] flowNodeKeyPair = generateKeyPair();
        byte[] flowNodePubkey = flowNodeKeyPair[0];
        byte[] flowNodePrikey = flowNodeKeyPair[1];

        byte[] centralPubkey = ByteArrayUtil.base64ToBytes(centralPubkeyBase64);

        testFlowNodeRegisterApi(flowNodePubkey, flowNodePrikey, registerDifficultyTargetNbits);
        testCentralPubkeyEmpowerApi(flowNodePubkey, flowNodePrikey, centralPubkey);
        testFlowNodeLockedApi(flowNodePubkey, flowNodePrikey, centralPubkey);
    }

    /**
     * 流转节点注册接口测试
     *
     * @param flowNodePubkey                流转节点公钥
     * @param flowNodePrikey                流转节点私钥
     * @param registerDifficultyTargetNbits 注册难度目标nBits
     */
    void testFlowNodeRegisterApi(
            byte[] flowNodePubkey,
            byte[] flowNodePrikey,
            int registerDifficultyTargetNbits
    ) {
        StopWatch stopWatch = new StopWatch("流转节点注册接口测试");

        byte[] testData;
        byte[] verfyData;

        short msgType = 0;
        UUID uuid = UUID.randomUUID();
        BigInteger registerDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(registerDifficultyTargetNbits));

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
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 流转节点授权中心公钥接口测试
     *
     * @param flowNodePubkey 流转节点公钥
     * @param flowNodePrikey 流转节点私钥
     * @param centralPubkey  中心公钥
     */
    void testCentralPubkeyEmpowerApi(byte[] flowNodePubkey, byte[] flowNodePrikey, byte[] centralPubkey) {
        byte[] testData;
        byte[] verfyData;

        short msgType = 1;
        UUID uuid = UUID.randomUUID();

        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, flowNodePubkey);
        verfyData = ArrayUtils.addAll(verfyData, centralPubkey);

        try {
            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
            );
            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));

            mockMvc.perform(post("/central-pubkey-empower-msg/send")
                            .contentType("application/octet-stream")
                            .content(testData)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 中心公钥锁定消息接口测试
     *
     * @param centralPubkey 中心公钥
     * @param centralPrikey 中心私钥
     */
    void saveCentralPubkeyLockedMsg(
            byte[] centralPubkey,
            byte[] centralPrikey
    ) {
        byte[] testData;
        byte[] verfyData;

        short msgType = 2;
        UUID uuid = UUID.randomUUID();

        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, centralPubkey);

        try {
            byte[] centralSignPre = Secp256k1EncryptUtil.signData(verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(centralPrikey)
            );
            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(centralSignPre));

            mockMvc.perform(post("/central-pubkey-locked-msg/send")
                            .contentType("application/octet-stream")
                            .content(testData)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 流转节点冻结接口测试
     *
     * @param flowNodePubkey 流转节点公钥
     * @param flowNodePrikey 流转节点私钥
     * @param centralPubkey  中心公钥
     */
    void testFlowNodeLockedApi(
            byte[] flowNodePubkey,
            byte[] flowNodePrikey,
            byte[] centralPubkey
    ) {
        byte[] testData;
        byte[] verfyData;

        short msgType = 3;
        UUID uuid = UUID.randomUUID();

        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, flowNodePubkey);
        verfyData = ArrayUtils.addAll(verfyData, centralPubkey);

        try {
            byte[] flowNodeSign = Secp256k1EncryptUtil.signData(verfyData,
                    Secp256k1EncryptUtil.rawToPrivateKey(flowNodePrikey)
            );
            testData = ArrayUtils.addAll(verfyData, Secp256k1EncryptUtil.derToRs(flowNodeSign));

            mockMvc.perform(post("/flow-node-locked-msg/send")
                            .contentType("application/octet-stream")
                            .content(testData)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 交易记录接口测试
     *
     * @param consumeNodePubkey                消费节点公钥
     * @param consumeNodePrikey                消费节点私钥
     * @param flowNodePubkey                   流转节点公钥
     * @param flowNodePrikey                   流转节点私钥
     * @param centralPubkey                    中心公钥
     * @param transactionDifficultyTargetNbits 交易难度目标nBits
     */
    UUID testTransactionRecordApi(
            byte[] consumeNodePubkey,
            byte[] consumeNodePrikey,
            byte[] flowNodePubkey,
            byte[] flowNodePrikey,
            byte[] centralPubkey,
            int transactionDifficultyTargetNbits
    ) {
        byte[] testData;
        byte[] verfyData;

        short msgType = 4;
        UUID uuid = UUID.randomUUID();
        long amount = RandomUtils.nextLong();
        short currencyType = 1;
        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));

        // 【信息类型2字节(4)】+【uuid16字节】+【金额8字节】+【货币类型2字节】+【交易难度目标4字节】+【随机数4字节】+【消费节点公钥33字节】+【流转节点公钥33字节】+【中心公钥33字节】
        // +【消费节点对信息(前9项数据)签名64字节】+【流转节点对信息(*前9项数据，也是前9项，方便两者同时签名)签名64字节】
        verfyData = ArrayUtils.addAll(ByteArrayUtil.shortToBytes(msgType),
                ByteArrayUtil.uuidToBytes(uuid)
        );
        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.longToBytes(amount));
        verfyData = ArrayUtils.addAll(verfyData, ByteArrayUtil.shortToBytes(currencyType));
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

            mockMvc.perform(post("/transaction-record-msg/send")
                            .contentType("application/octet-stream")
                            .content(testData)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return uuid;
    }

    /**
     * 交易挂载接口测试
     *
     * @param recordUuid                       挂载的交易记录信息的UUID
     * @param consumeNodePubkey                挂载的交易信息的消费节点公钥
     * @param consumeNodePrikey                挂载的交易信息的消费节点私钥
     * @param flowNodePubkey                   挂载的流转节点公钥
     * @param flowNodePrikey                   挂载的流转节点私钥
     * @param centralPubkey                    中心公钥
     * @param transactionDifficultyTargetNbits 交易难度目标nBits
     */
    void testTransactionMountApi(
            UUID recordUuid,
            byte[] consumeNodePubkey,
            byte[] consumeNodePrikey,
            byte[] flowNodePubkey,
            byte[] flowNodePrikey,
            byte[] centralPubkey,
            int transactionDifficultyTargetNbits
    ) {
        byte[] testData;
        byte[] verfyData;

        short msgType = 5;
        UUID uuid = UUID.randomUUID();
        BigInteger transactionDifficultyTarget = PoWUtil.calculateTargetFromNBits(ByteArrayUtil.intToBytes(transactionDifficultyTargetNbits));

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
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取回流率
     *
     * @param sourceFlowNodePubkey 源流转节点公钥
     * @param targetFlowNodePubkey 目标流转节点公钥
     */
    void getReturningFlowRate(
            byte[] sourceFlowNodePubkey,
            byte[] targetFlowNodePubkey
    ) {
        String testData = ByteArrayUtil.bytesToHex(sourceFlowNodePubkey) + "/" + ByteArrayUtil.bytesToHex(targetFlowNodePubkey);

        try {
            mockMvc.perform(get("/returning-flow-rate/" + testData)
                            .param("currencyType", "1")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(result -> {
                        String response = result.getResponse().getContentAsString();
                        logger.info("Response: {}", response);
                    });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 生成密钥对
     *
     * @return 包含公钥和私钥的字节数组，公钥在前，私钥在后
     */
    byte[][] generateKeyPair() {
        Security.addProvider(new BouncyCastleProvider());
        try {
            KeyPair keyPair = Secp256k1EncryptUtil.generateKeyPair();
            byte[] publicKeyBytes = Secp256k1EncryptUtil.publicKeyToCompressed(keyPair.getPublic());
            byte[] privateKeyBytes = Secp256k1EncryptUtil.privateKeyToRaw(keyPair.getPrivate());
            System.out.println("Public Key: " + ByteArrayUtil.bytesToHex(publicKeyBytes));
            System.out.println("Private Key: " + ByteArrayUtil.bytesToHex(privateKeyBytes));
            return new byte[][]{publicKeyBytes, privateKeyBytes};
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
