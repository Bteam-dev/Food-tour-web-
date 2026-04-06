package com.example.FoodTourApp.config;

import com.example.FoodTourApp.event.EsProductSyncEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for:
 * 1. ES sync event queues (chatbot + search)
 * 2. Token blacklist
 * 3. Rate limiting (login, register, OTP)
 */
@Configuration
public class RedisConfig {
    
    /**
     * ES Product Sync Event Queue - for chatbot and search index sync
     */
    @Bean
    public RedisTemplate<String, EsProductSyncEvent> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, EsProductSyncEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Key serializer
        template.setKeySerializer(new StringRedisSerializer());
        
        // Value serializer - JSON for EsProductSyncEvent
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        Jackson2JsonRedisSerializer<EsProductSyncEvent> serializer = 
                new Jackson2JsonRedisSerializer<>(objectMapper, EsProductSyncEvent.class);
        
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
    /**
     * Generic String RedisTemplate - for token blacklist and rate limiting
     */
    @Bean(name = "customStringRedisTemplate")
    @Primary
    public RedisTemplate<String, String> customStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
