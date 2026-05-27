package com.example.FoodTourApp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * TaskScheduler dùng cho ShopOpenStatusScheduler (event-driven, không polling).
     * Pool size nhỏ là đủ vì tasks rất nhẹ (chỉ gọi ES update khi shop đổi trạng thái).
     */
    @Bean
    public TaskScheduler shopTransitionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ShopOpenScheduler-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
        return scheduler;
    }
}

