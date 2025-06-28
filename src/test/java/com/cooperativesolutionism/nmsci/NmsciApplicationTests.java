package com.cooperativesolutionism.nmsci;

import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import com.cooperativesolutionism.nmsci.util.Secp256k1EncryptUtil;
import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Security;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
class NmsciApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Value("${central-key-pair.pubkey}")
    private String centralPubkeyBase64;

    @Test
    void contextLoads() {
    }

    @Test
    void centralPubkeyEmpowerMsgControllerTest() {
        Security.addProvider(new BouncyCastleProvider());

        byte[] testData;
        byte[] verfyData;

        short msgType = 0;
        UUID uuid = UUID.randomUUID();
        byte[] flowNodePubkey = Base64.getDecoder().decode("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI");
        byte[] flowNodePrikey = Base64.getDecoder().decode("qZaEg1hS+yR89ky9uNN2acpk0C7F9KeUpEBitso27Mw=");
        byte[] centralPubkey = Base64.getDecoder().decode(centralPubkeyBase64);

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
