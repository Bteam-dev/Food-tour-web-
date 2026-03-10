package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.Product;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

public interface FoodVectorService {

    void syncAllProducts();

    void syncProduct(Product product);

    void deleteProduct(Integer productId);

    ContentRetriever getContentRetriever();
}
