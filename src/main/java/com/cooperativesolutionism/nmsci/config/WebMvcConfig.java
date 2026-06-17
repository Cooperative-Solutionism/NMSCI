package com.cooperativesolutionism.nmsci.config;

import com.cooperativesolutionism.nmsci.config.properties.NmsciProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final NmsciProperties nmsciProperties;

    public WebMvcConfig(NmsciProperties nmsciProperties) {
        this.nmsciProperties = nmsciProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String rootDir = System.getProperty("user.dir");
        Path datFilepath = Paths.get(rootDir, nmsciProperties.getFileRootDir(), nmsciProperties.getFileDatDir());
        Path sourceCodeFilepath = Paths.get(rootDir, nmsciProperties.getFileRootDir(), nmsciProperties.getFileSourceCodeDir());

        registry.addResourceHandler("/dat/**").addResourceLocations("file:" + datFilepath + "/");
        registry.addResourceHandler("/source-code/**").addResourceLocations("file:" + sourceCodeFilepath + "/");
    }
}
