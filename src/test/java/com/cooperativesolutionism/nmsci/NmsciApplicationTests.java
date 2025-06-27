package com.cooperativesolutionism.nmsci;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Security;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class NmsciApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void centralPubkeyEmpowerMsgControllerTest() {
//        中心测试秘钥
//        Public Key Bytes: [2, 121, -94, 55, -55, 94, -40, 74, 12, 106, 76, -92, 61, -25, 78, 62, 88, -94, -115, 101, -97, -47, -68, -64, 63, -118, 84, -70, -118, -92, 107, -21, -55]
//        Private Key Bytes: [-47, -48, 51, -79, 0, 100, -61, -74, 95, -110, 112, 118, -105, 41, 95, -88, -57, -108, -59, 21, 28, 37, -65, 108, -86, 18, 15, -61, -101, -54, 4, 97]
        Security.addProvider(new BouncyCastleProvider());

        byte[] testData;
        byte[] verfyData;

        short msgType = 0;
        UUID uuid = UUID.randomUUID();
        byte[] flowNodePubkey = new byte[]{3, -86, -22, 16, -84, 123, 86, -58, -74, 115, 59, 31, -124, 103, 98, -29, 37, 28, 8, 65, 92, 8, -81, 51, -112, 18, 56, 122, -78, 45, 82, 115, -26};
        byte[] flowNodePrikey = new byte[]{8, -12, 101, -82, 97, -82, 124, -7, 54, -68, 117, -82, -22, -20, 85, -40, -70, 95, 74, 119, -42, -32, 14, 82, 8, -78, -37, -2, 17, -27, -38, -35};
        byte[] centralPubkey = new byte[]{2, 121, -94, 55, -55, 94, -40, 74, 12, 106, 76, -92, 61, -25, 78, 62, 88, -94, -115, 101, -97, -47, -68, -64, 63, -118, 84, -70, -118, -92, 107, -21, -55};

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
            ).andExpect(status().isOk());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
