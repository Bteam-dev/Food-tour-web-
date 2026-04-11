package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.event.ProductSyncEvent;
import com.example.FoodTourApp.service.EsChatbotSyncProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Producer for ES chatbot sync - pushes events to Redis queue.
 * Separate queue from search sync to allow independent processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EsChatbotSyncProducerImpl implements EsChatbotSyncProducer {
    
    private static final String REDIS_QUEUE_KEY = "es:chatbot:sync:queue";
    
    private final RedisTemplate<String, ProductSyncEvent> redisTemplate;
    
    @Override
    public void pushIndexEvent(Integer productId) {
        try {
            ProductSyncEvent event = ProductSyncEvent.index(productId);
            redisTemplate.opsForList().rightPush(REDIS_QUEUE_KEY, event);
            log.debug("Pushed INDEX event to chatbot queue: productId={}", productId);
        } catch (Exception e) {
            log.error("Failed to push chatbot INDEX event for product {}: {}", 
                    productId, e.getMessage());
        }
    }
    
    @Override
    public void pushDeleteEvent(Integer productId) {
        try {
            ProductSyncEvent event = ProductSyncEvent.delete(productId);
            redisTemplate.opsForList().rightPush(REDIS_QUEUE_KEY, event);
            log.debug("Pushed DELETE event to chatbot queue: productId={}", productId);
        } catch (Exception e) {
            log.error("Failed to push chatbot DELETE event for product {}: {}", 
                    productId, e.getMessage());
        }
    }
}
