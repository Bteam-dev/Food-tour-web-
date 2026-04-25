package com.example.FoodTourApp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storageFilePath = "file:///D:/Project/BackEnd/FoodTourApp_BE/StorageFile/";

        // Cache control: 7 ngày cho static images
        int cachePeriodSeconds = 604800; // 7 days

        // Serve các file public (ảnh sản phẩm, avatar, logo shop, ...) - KHÔNG bao gồm FileMessage
        registry.addResourceHandler("/uploads/ProductImage/**")
                .addResourceLocations(storageFilePath + "ProductImage/")
                .setCachePeriod(cachePeriodSeconds)
                .resourceChain(true);
        registry.addResourceHandler("/uploads/ShopLogo/**")
                .addResourceLocations(storageFilePath + "ShopLogo/")
                .setCachePeriod(cachePeriodSeconds)
                .resourceChain(true);
        registry.addResourceHandler("/uploads/ShopBanner/**")
                .addResourceLocations(storageFilePath + "ShopBanner/")
                .setCachePeriod(cachePeriodSeconds)
                .resourceChain(true);
        registry.addResourceHandler("/uploads/ReviewImage/**")
                .addResourceLocations(storageFilePath + "ReviewImage/")
                .setCachePeriod(cachePeriodSeconds)
                .resourceChain(true);
        registry.addResourceHandler("/uploads/UserAvatar/**")
                .addResourceLocations(storageFilePath + "UserAvatar/")
                .setCachePeriod(cachePeriodSeconds)
                .resourceChain(true);
        registry.addResourceHandler("/uploads/ForDemo/**")
                .addResourceLocations(storageFilePath + "ForDemo/")
                .setCachePeriod(cachePeriodSeconds)
                .resourceChain(true);

        // BusinessLicense và IdCard KHÔNG serve tĩnh — phải đi qua /api/admin/files/** (yêu cầu ADMIN JWT)

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
