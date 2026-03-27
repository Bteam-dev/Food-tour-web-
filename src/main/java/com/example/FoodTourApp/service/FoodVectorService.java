package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.Product;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

import java.util.List;

public interface FoodVectorService {

    ContentRetriever getContentRetriever();

    List<Integer> retrieveProductIds(String query);

    /**
     * Sync một món ăn cụ thể khi có thay đổi (thêm/sửa/xóa)
     */
    void syncProductToEs(Integer productId);

    /**
     * Full sync toàn bộ món ăn (dùng cho admin hoặc khi cần reset)
     */
    void fullSyncToEs();
}