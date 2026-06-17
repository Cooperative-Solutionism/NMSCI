package com.cooperativesolutionism.nmsci.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebMvcConfigTest {

    @Test
    void contributesResourceHandlersWithoutReplacingBootMvcAutoConfiguration() {
        assertTrue(WebMvcConfigurer.class.isAssignableFrom(WebMvcConfig.class));
        assertFalse(WebMvcConfigurationSupport.class.isAssignableFrom(WebMvcConfig.class));
    }
}
