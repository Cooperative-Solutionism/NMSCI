package com.cooperativesolutionism.nmsci.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig extends WebMvcConfigurationSupport {

    @Value("${file-root-dir}")
    private String fileRootDir;

    @Value("${file-dat-dir}")
    private String fileDatDir;

    @Value("${file-source-code-dir}")
    private String fileSourceCodeDir;

     @Override
     public void addResourceHandlers(ResourceHandlerRegistry registry) {
         String rootDir = System.getProperty("user.dir");
         Path datFilepath = Paths.get(rootDir, fileRootDir, fileDatDir);
         Path sourceCodeFilepath = Paths.get(rootDir, fileRootDir, fileSourceCodeDir);

         registry.addResourceHandler("/dat/**").addResourceLocations("file:" + datFilepath + "/");
         registry.addResourceHandler("/source-code/**").addResourceLocations("file:" + sourceCodeFilepath + "/");
         super.addResourceHandlers(registry);
     }
}
