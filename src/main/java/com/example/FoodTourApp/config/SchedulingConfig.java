package com.example.FoodTourApp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
    // Bật tính năng scheduled tasks để tự động xóa token hết hạn mỗi giờ
}

