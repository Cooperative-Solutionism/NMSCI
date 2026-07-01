package com.cooperativesolutionism.nmsci.concurrency;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HibernateBatchConfigurationContractTest {

    @Test
    void applicationPropertiesEnableHibernateBatchWrites() throws IOException {
        String applicationProperties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(applicationProperties.contains("spring.jpa.properties.hibernate.jdbc.batch_size="));
        assertTrue(applicationProperties.contains("spring.jpa.properties.hibernate.order_inserts=true"));
        assertTrue(applicationProperties.contains("spring.jpa.properties.hibernate.order_updates=true"));
    }

    @Test
    void applicationPropertiesEnableMigrationValidationAndGracefulShutdown() throws IOException {
        String applicationProperties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(applicationProperties.contains("spring.flyway.baseline-version=1"));
        assertTrue(applicationProperties.contains("spring.jpa.hibernate.ddl-auto=validate"));
        assertTrue(applicationProperties.contains("server.shutdown=graceful"));
    }
}
