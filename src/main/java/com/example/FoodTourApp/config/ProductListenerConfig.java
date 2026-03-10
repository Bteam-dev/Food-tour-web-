package com.example.FoodTourApp.config;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.service.FoodVectorService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreRemove;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ProductListenerConfig implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        ProductListenerConfig.applicationContext = ctx;
    }

    private FoodVectorService getVectorService() {
        return applicationContext.getBean(FoodVectorService.class);
    }

    @PostPersist
    @PostUpdate
    public void onSave(Product product) {
        getVectorService().syncProduct(product);
    }

    @PreRemove
    public void onDelete(Product product) {
        getVectorService().deleteProduct(product.getId());
    }
}