package com.example.FoodTourApp.config;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.service.EsChatbotSyncProducer;
import com.example.FoodTourApp.service.EsProductSyncProducer;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener - Auto sync Product changes to Elasticsearch.
 * 
 * Triggers when Product entity is created/updated/deleted.
 * Publishes events to Redis queues for async processing.
 * 
 * Syncs to 2 ES indexes:
 * 1. foodtour_products_chatbot - Vector index for RAG/chatbot (with embeddings)
 * 2. foodtour_products_search  - Search index for product listing
 * 
 * Flow:
 * Product change → this listener → Redis queue → Consumer → ES bulk sync
 */
@Component
public class ProductEntityListener {

    private static final Logger log = LoggerFactory.getLogger(ProductEntityListener.class);

    @Autowired
    private EsChatbotSyncProducer esChatbotSyncProducer;

    @Autowired
    private EsProductSyncProducer esProductSyncProducer;

    @PostPersist
    @PostUpdate
    public void onSaveOrUpdate(Product product) {
        Integer productId = product.getId();
        log.debug("Product {} saved/updated, queuing ES sync events", productId);
        
        // 1. Push event to chatbot sync queue (with embedding generation)
        try {
            esChatbotSyncProducer.pushIndexEvent(productId);
        } catch (Exception e) {
            log.error("Failed to queue product {} for chatbot index: {}", productId, e.getMessage());
        }
        
        // 2. Push event to search sync queue (fast, non-blocking)
        try {
            esProductSyncProducer.pushIndexEvent(productId);
        } catch (Exception e) {
            log.error("Failed to queue product {} for search index: {}", productId, e.getMessage());
        }
    }

    @PostRemove
    public void onDelete(Product product) {
        Integer productId = product.getId();
        log.debug("Product {} deleted, queuing ES delete events", productId);
        
        // 1. Push delete event to chatbot queue
        try {
            esChatbotSyncProducer.pushDeleteEvent(productId);
        } catch (Exception e) {
            log.error("Failed to queue product {} delete for chatbot index: {}", productId, e.getMessage());
        }
        
        // 2. Push delete event to search queue
        try {
            esProductSyncProducer.pushDeleteEvent(productId);
        } catch (Exception e) {
            log.error("Failed to queue product {} delete for search index: {}", productId, e.getMessage());
        }
    }
}
