package com.example.FoodTourApp.config;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.service.FoodVectorService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProductEsSyncListener {

    @Autowired
    private FoodVectorService foodVectorService;

    @PostPersist
    @PostUpdate
    public void onSaveOrUpdate(Product product) {
        foodVectorService.syncProductToEs(product.getId());
    }

    @PostRemove
    public void onDelete(Product product) {
        foodVectorService.syncProductToEs(product.getId());
    }
}