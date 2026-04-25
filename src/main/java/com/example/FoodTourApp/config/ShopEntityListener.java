package com.example.FoodTourApp.config;

import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.service.EsChatbotSyncProducer;
import com.example.FoodTourApp.service.EsProductSyncProducer;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Entity Listener - Auto cascade Shop changes to Elasticsearch.
 *
 * Khi shop thay đổi (approve/reject/update info/deactivate),
 * tất cả products của shop đó được re-index vào ES để phản ánh dữ liệu mới
 * (shop_name, shop_is_verified, shop_is_active, shop_address, ...).
 *
 * Flow:
 * Shop change → this listener → push all product IDs to Redis queues
 *   → EsChatbotSyncConsumer (5s) → embed + index foodtour_products_chatbot
 *   → EsProductSyncConsumer  (2s) → index foodtour_products_search
 */
@Component
public class ShopEntityListener {

    private static final Logger log = LoggerFactory.getLogger(ShopEntityListener.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private EsChatbotSyncProducer esChatbotSyncProducer;

    @Autowired
    private EsProductSyncProducer esProductSyncProducer;

    /**
     * Khi shop được update (approve, reject, đổi tên, đổi địa chỉ, deactivate...):
     * Re-index tất cả products của shop để ES phản ánh dữ liệu shop mới nhất.
     */
    @PostUpdate
    public void onShopUpdate(Shop shop) {
        try {
            var products = productRepository.findByShop(shop);
            if (products.isEmpty()) return;

            log.info("Shop {} updated — queuing re-index for {} products", shop.getId(), products.size());

            for (var product : products) {
                Integer productId = product.getId();
                try {
                    esChatbotSyncProducer.pushIndexEvent(productId);
                } catch (Exception e) {
                    log.error("Failed to queue chatbot re-index for product {} (shop {}): {}",
                            productId, shop.getId(), e.getMessage());
                }
                try {
                    esProductSyncProducer.pushIndexEvent(productId);
                } catch (Exception e) {
                    log.error("Failed to queue search re-index for product {} (shop {}): {}",
                            productId, shop.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("ShopEntityListener.onShopUpdate failed for shop {}: {}", shop.getId(), e.getMessage());
        }
    }

    /**
     * Khi shop bị hard-delete: xóa tất cả products của shop khỏi ES.
     * (Thường là soft-delete qua isActive=false → @PostUpdate đã xử lý)
     */
    @PostRemove
    public void onShopDelete(Shop shop) {
        try {
            var products = productRepository.findByShop(shop);
            if (products.isEmpty()) return;

            log.info("Shop {} deleted — queuing ES delete for {} products", shop.getId(), products.size());

            for (var product : products) {
                Integer productId = product.getId();
                try {
                    esChatbotSyncProducer.pushDeleteEvent(productId);
                } catch (Exception e) {
                    log.error("Failed to queue chatbot delete for product {} (shop {}): {}",
                            productId, shop.getId(), e.getMessage());
                }
                try {
                    esProductSyncProducer.pushDeleteEvent(productId);
                } catch (Exception e) {
                    log.error("Failed to queue search delete for product {} (shop {}): {}",
                            productId, shop.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("ShopEntityListener.onShopDelete failed for shop {}: {}", shop.getId(), e.getMessage());
        }
    }
}
