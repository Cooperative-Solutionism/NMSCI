package com.cooperativesolutionism.nmsci.database;

import com.cooperativesolutionism.nmsci.NmsciApplication;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled
@AutoConfigureMockMvc
@SpringBootTest(classes = NmsciApplication.class)
public class MigrateDataTest {

    @Test
    void contextLoads() {
    }

}
