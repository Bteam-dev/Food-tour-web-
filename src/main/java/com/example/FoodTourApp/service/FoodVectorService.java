package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.Product;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

import java.util.List;

public interface FoodVectorService {

    void syncAllProducts();

    void syncProduct(Product product);

    void deleteProduct(Integer productId);

    ContentRetriever getContentRetriever();
    
    /**
     * Retrieve relevant product IDs based on user query
     * Used to generate navigation URLs for chatbot responses
     * @param query User's question
     * @return List of product IDs that match the query
     */
    List<Integer> retrieveProductIds(String query);
}
