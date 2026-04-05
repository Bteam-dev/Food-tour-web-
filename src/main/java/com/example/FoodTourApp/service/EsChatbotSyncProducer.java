package com.example.FoodTourApp.service;

/**
 * Producer for pushing ES chatbot sync events to Redis queue.
 * Separate from search sync to allow independent scaling.
 */
public interface EsChatbotSyncProducer {
    
    /**
     * Push INDEX event to chatbot sync queue
     */
    void pushIndexEvent(Integer productId);
    
    /**
     * Push DELETE event to chatbot sync queue
     */
    void pushDeleteEvent(Integer productId);
}
