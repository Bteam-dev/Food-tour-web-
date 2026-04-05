package com.example.FoodTourApp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event representing a product change that needs to be synced to Elasticsearch.
 * 
 * Flow:
 * 1. JPA Listener detects product change
 * 2. Push this event to Redis queue
 * 3. Consumer picks up events in batches
 * 4. Consumer syncs to ES using Java client (not Python script)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EsProductSyncEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public enum Action {
        INDEX,   // Create or update document
        DELETE   // Delete document
    }
    
    private Integer productId;
    private Action action;
    private Instant timestamp;
    
    public static EsProductSyncEvent index(Integer productId) {
        return new EsProductSyncEvent(productId, Action.INDEX, Instant.now());
    }
    
    public static EsProductSyncEvent delete(Integer productId) {
        return new EsProductSyncEvent(productId, Action.DELETE, Instant.now());
    }
}
