package com.example.FoodTourApp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storageFilePath = "file:///D:/Project/BackEnd/FoodTourApp_BE/StorageFile/";

        // Serve các file public (ảnh sản phẩm, avatar, logo shop, ...) - KHÔNG bao gồm FileMessage
        registry.addResourceHandler("/uploads/ProductImage/**")
                .addResourceLocations(storageFilePath + "ProductImage/");
        registry.addResourceHandler("/uploads/ShopLogo/**")
                .addResourceLocations(storageFilePath + "ShopLogo/");
        registry.addResourceHandler("/uploads/ShopBanner/**")
                .addResourceLocations(storageFilePath + "ShopBanner/");
        registry.addResourceHandler("/uploads/ReviewImage/**")
                .addResourceLocations(storageFilePath + "ReviewImage/");
        registry.addResourceHandler("/uploads/UserAvatar/**")
                .addResourceLocations(storageFilePath + "UserAvatar/");

        // FileMessage KHÔNG được serve tĩnh ở đây nữa
        // → phải đi qua GET /api/user/chat/files/** để kiểm tra JWT + participant
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
