package com.example.FoodTourApp.service;

/**
 * Producer for pushing recommend sync events to Redis queue.
 * Triggered by ProductEntityListener when product is created/updated/deleted.
 */
public interface EsRecommendSyncProducer {
    void pushIndexEvent(Integer productId);
    void pushDeleteEvent(Integer productId);
}
