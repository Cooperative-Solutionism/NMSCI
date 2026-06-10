package com.cooperativesolutionism.nmsci.support;

import com.cooperativesolutionism.nmsci.NmsciApplication;
import com.cooperativesolutionism.nmsci.task.GenerateBlockTask;
import com.cooperativesolutionism.nmsci.util.ByteArrayUtil;
import jakarta.annotation.Resource;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.security.Security;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@ExtendWith(DockerAvailableCondition.class)
@SpringBootTest(classes = NmsciApplication.class)
public abstract class NmsciIntegrationTestBase {

    public static final int REGISTER_DIFFICULTY_NBITS = 0x20ffffff;
    public static final int TRANSACTION_DIFFICULTY_NBITS = 0x20ffffff;
    private static final String INTEGRATION_TESTS_ENABLED_PROPERTY = "nmsci.integration-tests.enabled";
    private static final String DOCKER_API_VERSION_PROPERTY = "api.version";
    private static final String DEFAULT_DOCKER_API_VERSION = "1.40";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nmsci_test")
            .withUsername("postgres")
            .withPassword("postgres")
            .withInitScript("database/schema.sql");

    private static volatile boolean postgresStarted;

    @Resource
    protected MockMvc mockMvc;

    @Resource
    protected DatabaseTestHelper databaseTestHelper;

    @MockitoBean
    private GenerateBlockTask generateBlockTask;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> postgres().getJdbcUrl());
        registry.add("spring.datasource.username", () -> postgres().getUsername());
        registry.add("spring.datasource.password", () -> postgres().getPassword());
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.task.scheduling.enabled", () -> "false");
        registry.add("central-key-pair.pubkey", () -> TestKeyPairs.CENTRAL.pubkeyBase64());
        registry.add("central-key-pair.prikey", () -> TestKeyPairs.CENTRAL.prikeyBase64());
        registry.add("register-difficulty-target-nbits", () -> "0x20ffffff");
        registry.add("transaction-difficulty-target-nbits", () -> "0x20ffffff");
        registry.add("file-root-dir", () -> "target/nmsci-test-files");
        registry.add("file-dat-dir", () -> "dat");
        registry.add("file-source-code-dir", () -> "source-code");
    }

    @BeforeAll
    static void addBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @BeforeEach
    void resetDatabase() {
        databaseTestHelper.resetWithLatestBlock(
                REGISTER_DIFFICULTY_NBITS,
                TRANSACTION_DIFFICULTY_NBITS,
                ByteArrayUtil.base64ToBytes(TestKeyPairs.CENTRAL.pubkeyBase64())
        );
    }

    static boolean isDockerAvailable() {
        configureDockerApiVersion();
        if (!isIntegrationTestsEnabled()) {
            return false;
        }

        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    static String disabledReason() {
        if (!isIntegrationTestsEnabled()) {
            return "NMSCI integration tests disabled by " + INTEGRATION_TESTS_ENABLED_PROPERTY + "=false";
        }
        return "Docker is not available; skipping Testcontainers-backed NMSCI integration tests";
    }

    private static PostgreSQLContainer<?> postgres() {
        configureDockerApiVersion();
        if (!postgresStarted) {
            synchronized (POSTGRES) {
                if (!postgresStarted) {
                    POSTGRES.start();
                    postgresStarted = true;
                }
            }
        }
        return POSTGRES;
    }

    private static boolean isIntegrationTestsEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty(INTEGRATION_TESTS_ENABLED_PROPERTY));
    }

    private static void configureDockerApiVersion() {
        if (System.getProperty(DOCKER_API_VERSION_PROPERTY) == null) {
            System.setProperty(DOCKER_API_VERSION_PROPERTY, DEFAULT_DOCKER_API_VERSION);
        }
    }
}
