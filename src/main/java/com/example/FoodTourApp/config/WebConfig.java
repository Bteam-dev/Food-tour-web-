package com.example.FoodTourApp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve file từ thư mục StorageFile
        String storageFilePath = "file:///D:/Project/BackEnd/FoodTourApp_BE/StorageFile/";

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(storageFilePath);

        // Log để debug
        System.out.println("Static resource handler registered: /uploads/** -> " + storageFilePath);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
