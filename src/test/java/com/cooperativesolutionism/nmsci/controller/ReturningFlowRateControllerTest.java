package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.NmsciApplication;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Disabled
@AutoConfigureMockMvc
@SpringBootTest(classes = NmsciApplication.class)
class ReturningFlowRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getReturningFlowRate() {
        String sourceFlowNodePubkey = ByteArrayUtil.bytesToHex(ByteArrayUtil.base64ToBytes("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI"));
        String targetFlowNodePubkey = ByteArrayUtil.bytesToHex(ByteArrayUtil.base64ToBytes("AjQ2H9M/OTpDs0caRjSe+cR5Ru4sUQSDP0Ime9PTwIGI"));
        String testData = sourceFlowNodePubkey + "/" + targetFlowNodePubkey;

        try {
            mockMvc.perform(get("/returning-flow-rate/" + testData)
                            .param("currencyType", "1")
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