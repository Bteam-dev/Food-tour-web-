package com.example.FoodTourApp.service;

import java.util.List;

/**
 * Face detection (YuNet) + 5-point alignment + ArcFace embedding extraction.
 */
public interface FaceEmbeddingExtractorService {

    float[] extractEmbedding(String base64Image) throws Exception;

    float[] averageEmbeddings(List<float[]> embeddings);

    double cosineSimilarity(float[] a, float[] b);
}
