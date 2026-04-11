package com.example.FoodTourApp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Event DTO representing a product change that needs to be synced to Elasticsearch.
 * 
 * Used by:
 * - ProductEntityListener: creates events when Product entity changes
 * - EsChatbotSyncProducer/EsProductSyncProducer: pushes to Redis queue
 * - EsChatbotSyncConsumer/EsProductSyncConsumer: processes in batches
 * 
 * Flow:
 * Product change → ProductEntityListener → Redis queue → Consumer → ES bulk sync
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSyncEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    public enum Action {
        INDEX,   // Create or update document
        DELETE   // Delete document
    }
    
    private Integer productId;
    private Action action;
    private Instant timestamp;
    
    public static ProductSyncEvent index(Integer productId) {
        return new ProductSyncEvent(productId, Action.INDEX, Instant.now());
    }
    
    public static ProductSyncEvent delete(Integer productId) {
        return new ProductSyncEvent(productId, Action.DELETE, Instant.now());
    }
}
