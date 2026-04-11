package com.example.FoodTourApp.service;

import com.example.FoodTourApp.event.ProductSyncEvent;

/**
 * Producer interface for ES product sync events.
 * Pushes events to Redis queue for async processing.
 */
public interface EsProductSyncProducer {
    
    /**
     * Push an index event (create/update product in ES)
     */
    void pushIndexEvent(Integer productId);
    
    /**
     * Push a delete event (remove product from ES)
     */
    void pushDeleteEvent(Integer productId);
    
    /**
     * Push a raw event
     */
    void pushEvent(ProductSyncEvent event);
}
