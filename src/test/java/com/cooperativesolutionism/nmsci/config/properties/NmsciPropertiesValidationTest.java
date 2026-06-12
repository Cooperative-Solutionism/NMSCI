package com.cooperativesolutionism.nmsci.config.properties;

import com.cooperativesolutionism.nmsci.support.TestKeyPairs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NmsciPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(validProperties());

    @Test
    void startsWithValidProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(NmsciProperties.class).getCentralPubkeyBase64())
                    .isEqualTo(TestKeyPairs.CENTRAL.pubkeyBase64());
        });
    }

    @Test
    void failsFastWhenCentralPubkeyIsInvalid() {
        contextRunner
                .withPropertyValues("central-key-pair.pubkey=not-base64")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenSourceCodeHashIsInvalid() {
        contextRunner
                .withPropertyValues("source-code-zip-hash=bad")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenBlockSizeIsNotPositive() {
        contextRunner
                .withPropertyValues("block-max-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenBlockMaxSizeCannotFitHeader() {
        contextRunner
                .withPropertyValues("block-header-size=229", "block-max-size=128")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenDatMaxSizeCannotFitOneBlock() {
        contextRunner
                .withPropertyValues("block-max-size=1048576", "block-dat-max-size=1048576")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsFastWhenPathIsBlank() {
        contextRunner
                .withPropertyValues("file-root-dir=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] validProperties() {
        return new String[]{
                "central-key-pair.pubkey=" + TestKeyPairs.CENTRAL.pubkeyBase64(),
                "central-key-pair.prikey=" + TestKeyPairs.CENTRAL.prikeyBase64(),
                "block-version=1",
                "block-header-size=229",
                "block-max-size=1048576",
                "block-dat-max-size=134217728",
                "register-difficulty-target-nbits=0x20ffffff",
                "transaction-difficulty-target-nbits=0x20ffffff",
                "file-root-dir=target/nmsci-test-files",
                "file-dat-dir=dat",
                "file-source-code-dir=source-code",
                "source-code-zip-hash=0000000000000000000000000000000000000000000000000000000000000000"
        };
    }

    @EnableConfigurationProperties(NmsciProperties.class)
    static class TestConfig {
    }
}
