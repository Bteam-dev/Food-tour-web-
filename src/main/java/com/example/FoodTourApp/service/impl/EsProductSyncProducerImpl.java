package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.event.ProductSyncEvent;
import com.example.FoodTourApp.service.EsProductSyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-based implementation of EsProductSyncProducer.
 * Pushes sync events to a Redis List (queue).
 */
@Service
public class EsProductSyncProducerImpl implements EsProductSyncProducer {
    
    private static final Logger log = LoggerFactory.getLogger(EsProductSyncProducerImpl.class);
    
    /**
     * Redis key for the sync queue.
     * Using List (LPUSH/BRPOP) for reliable FIFO queue.
     */
    public static final String SYNC_QUEUE_KEY = "es:product:sync:queue";
    
    private final RedisTemplate<String, ProductSyncEvent> redisTemplate;
    
    public EsProductSyncProducerImpl(RedisTemplate<String, ProductSyncEvent> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @Override
    public void pushIndexEvent(Integer productId) {
        pushEvent(ProductSyncEvent.index(productId));
    }
    
    @Override
    public void pushDeleteEvent(Integer productId) {
        pushEvent(ProductSyncEvent.delete(productId));
    }
    
    @Override
    public void pushEvent(ProductSyncEvent event) {
        try {
            redisTemplate.opsForList().leftPush(SYNC_QUEUE_KEY, event);
            log.debug("Pushed ES sync event: {} for product {}", event.getAction(), event.getProductId());
        } catch (Exception e) {
            log.error("Failed to push ES sync event for product {}: {}", event.getProductId(), e.getMessage());
            // Don't throw - we don't want to fail the main transaction
        }
    }
}
