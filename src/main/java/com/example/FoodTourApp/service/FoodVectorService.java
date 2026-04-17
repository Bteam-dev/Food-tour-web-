package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.Product;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

import java.util.List;

public interface FoodVectorService {

    ContentRetriever getContentRetriever();

    List<Integer> retrieveProductIds(String query);

    /**
     * Full sync toàn bộ món ăn vào ES chatbot index.
     * Push tất cả product IDs vào Redis queue → EsChatbotSyncConsumer xử lý async.
     * Dùng khi: sửa code logic, fix index corrupt, lần đầu deploy.
     */
    void fullSyncToEs();
}