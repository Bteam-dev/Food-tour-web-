package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.event.ProductSyncEvent;
import com.example.FoodTourApp.service.EsRecommendSyncProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EsRecommendSyncProducerImpl implements EsRecommendSyncProducer {

    static final String REDIS_QUEUE_KEY = "rec:recommend:sync:queue";

    private final RedisTemplate<String, ProductSyncEvent> redisTemplate;

    @Override
    public void pushIndexEvent(Integer productId) {
        try {
            redisTemplate.opsForList().rightPush(REDIS_QUEUE_KEY, ProductSyncEvent.index(productId));
            log.debug("Pushed INDEX event to recommend queue: productId={}", productId);
        } catch (Exception e) {
            log.error("Failed to push recommend INDEX event for product {}: {}", productId, e.getMessage());
        }
    }

    @Override
    public void pushDeleteEvent(Integer productId) {
        try {
            redisTemplate.opsForList().rightPush(REDIS_QUEUE_KEY, ProductSyncEvent.delete(productId));
            log.debug("Pushed DELETE event to recommend queue: productId={}", productId);
        } catch (Exception e) {
            log.error("Failed to push recommend DELETE event for product {}: {}", productId, e.getMessage());
        }
    }
}
