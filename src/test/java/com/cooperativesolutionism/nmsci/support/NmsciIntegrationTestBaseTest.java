package com.cooperativesolutionism.nmsci.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NmsciIntegrationTestBaseTest {

    private static final String API_VERSION_PROPERTY = "api.version";
    private static final String MAVEN_DOCKER_API_VERSION_PROPERTY = "docker.api.version";

    private final String originalApiVersion = System.getProperty(API_VERSION_PROPERTY);
    private final String originalMavenDockerApiVersion = System.getProperty(MAVEN_DOCKER_API_VERSION_PROPERTY);

    @AfterEach
    void restoreProperties() {
        restore(API_VERSION_PROPERTY, originalApiVersion);
        restore(MAVEN_DOCKER_API_VERSION_PROPERTY, originalMavenDockerApiVersion);
    }

    @Test
    void configuresDockerClientApiVersionFromMavenProperty() {
        System.clearProperty(API_VERSION_PROPERTY);
        System.setProperty(MAVEN_DOCKER_API_VERSION_PROPERTY, "1.41");

        NmsciIntegrationTestBase.configureDockerApiVersion();

        assertEquals("1.41", System.getProperty(API_VERSION_PROPERTY));
    }

    @Test
    void keepsExplicitDockerClientApiVersionOverride() {
        System.setProperty(API_VERSION_PROPERTY, "1.44");
        System.setProperty(MAVEN_DOCKER_API_VERSION_PROPERTY, "1.41");

        NmsciIntegrationTestBase.configureDockerApiVersion();

        assertEquals("1.44", System.getProperty(API_VERSION_PROPERTY));
    }

    @Test
    void configuresIdeFallbackWhenMavenPropertyIsMissing() {
        System.clearProperty(API_VERSION_PROPERTY);
        System.clearProperty(MAVEN_DOCKER_API_VERSION_PROPERTY);

        NmsciIntegrationTestBase.configureDockerApiVersion();

        assertEquals("1.40", System.getProperty(API_VERSION_PROPERTY));
    }

    private static void restore(String propertyName, String originalValue) {
        if (originalValue == null) {
            System.clearProperty(propertyName);
            return;
        }

        System.setProperty(propertyName, originalValue);
    }
}
