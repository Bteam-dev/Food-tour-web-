package com.example.FoodTourApp.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldSort;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.ProductVariant;
import com.example.FoodTourApp.repository.ProductVariantRepository;
import com.example.FoodTourApp.service.ProductSearchService;
import jakarta.annotation.PostConstruct;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Elasticsearch Search Service Implementation.
 * 
 * - SYNC: Gọi Python script (scripts/search/sync_es_search.py)
 * - SEARCH: Dùng ES Java client trực tiếp
 */
@Service
public class ProductSearchServiceImpl implements ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchServiceImpl.class);

    private final ProductVariantRepository variantRepository;
    private ElasticsearchClient esClient;

    @Value("${elasticsearch.host:localhost}")
    private String esHost;

    @Value("${elasticsearch.port:9200}")
    private int esPort;

    @Value("${elasticsearch.search-index:foodtour_products_search}")
    private String searchIndex;

    @Value("${app.python-search-sync-script:scripts/search/sync_es_search.py}")
    private String pythonSyncScript;

    public ProductSearchServiceImpl(ProductVariantRepository variantRepository) {
        this.variantRepository = variantRepository;
    }

    @PostConstruct
    public void init() {
        try {
            RestClient restClient = RestClient.builder(new HttpHost(esHost, esPort, "http")).build();
            RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            this.esClient = new ElasticsearchClient(transport);

            log.info("ProductSearchService initialized - ES index: {}", searchIndex);
        } catch (Exception e) {
            log.error("Failed to initialize ProductSearchService", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SYNC OPERATIONS (Python script)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @Async
    public void syncProductToEs(Integer productId) {
        executePythonScript("--product-id", String.valueOf(productId));
    }

    @Override
    @Async
    public void deleteProductFromEs(Integer productId) {
        executePythonScript("--delete-product-id", String.valueOf(productId));
    }

    @Override
    public void fullSyncToEs() {
        executePythonScript("--full", null);
    }

    @Override
    public void recreateIndexAndSync() {
        executePythonScript("--recreate-index", null);
    }

    @Override
    public void updateSalesStatistics() {
        executePythonScript("--update-sales", null);
    }

    private void executePythonScript(String mode, String param) {
        try {
            List<String> command = new ArrayList<>();
            command.add("python");
            command.add(pythonSyncScript);
            command.add(mode);
            if (param != null) {
                command.add(param);
            }

            log.info("[ES Search Sync] Executing: python {} {} {}", pythonSyncScript, mode, param != null ? param : "");

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(Paths.get(".").toAbsolutePath().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[ES Search Sync] {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("[ES Search Sync] Completed successfully");
            } else {
                log.warn("[ES Search Sync] Exited with code: {}", exitCode);
            }
        } catch (Exception e) {
            log.error("[ES Search Sync] Failed to execute script", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SEARCH OPERATIONS (ES Java client)
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public Page<ProductResponseDTO> searchProducts(
            String keyword,
            String city,
            Integer categoryId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            Pageable pageable
    ) {
        checkElasticsearchAvailable();

        try {
            // Build query
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            // Base filter: chỉ sản phẩm available
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            // Keyword search (full-text)
            if (keyword != null && !keyword.isBlank()) {
                String normalizedKeyword = normalizeVietnamese(keyword.trim());
                
                boolQuery.must(MultiMatchQuery.of(m -> m
                        .query(keyword.trim())
                        .fields(
                                "name^3",
                                "name_normalized^3",
                                "description^1.5",
                                "description_normalized^1.5",
                                "ingredients^1",
                                "tags^2",
                                "category_name^1.5",
                                "shop_name^1",
                                "search_text^1"
                        )
                        .type(TextQueryType.BestFields)
                        .fuzziness("AUTO")
                        .prefixLength(2)
                        .minimumShouldMatch("70%")
                )._toQuery());
            }

            // Filter by city
            if (city != null && !city.isBlank()) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("shop_city")
                        .value(city.trim().toLowerCase())
                )._toQuery());
            }

            // Filter by category
            if (categoryId != null) {
                boolQuery.filter(TermQuery.of(t -> t
                        .field("category_id")
                        .value(categoryId)
                )._toQuery());
            }

            // Filter by price range
            if (minPrice != null) {
                boolQuery.filter(RangeQuery.of(r -> r
                        .field("effective_price")
                        .gte(JsonData.of(minPrice.doubleValue()))
                )._toQuery());
            }
            if (maxPrice != null) {
                boolQuery.filter(RangeQuery.of(r -> r
                        .field("effective_price")
                        .lte(JsonData.of(maxPrice.doubleValue()))
                )._toQuery());
            }

            // Build sort
            List<SortOptions> sortOptions = buildSortOptions(sortBy);

            // Execute search
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .sort(sortOptions)
                            .from((int) pageable.getOffset())
                            .size(pageable.getPageSize())
                            .trackTotalHits(t -> t.enabled(true)),
                    Map.class
            );

            // Convert results
            List<ProductResponseDTO> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductResponseDTO)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            log.debug("ES Search: keyword='{}', city='{}', categoryId={}, results={}, total={}",
                    keyword, city, categoryId, results.size(), totalHits);

            return new PageImpl<>(results, pageable, totalHits);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Search failed", e);
            throw new RuntimeException("Elasticsearch search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Page<ProductResponseDTO> searchProductsByShop(
            Integer shopId,
            String keyword,
            String sortBy,
            Pageable pageable
    ) {
        checkElasticsearchAvailable();
        
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());
            boolQuery.filter(TermQuery.of(t -> t.field("shop_id").value(shopId))._toQuery());

            if (keyword != null && !keyword.isBlank()) {
                boolQuery.must(MatchQuery.of(m -> m
                        .field("name")
                        .query(keyword.trim())
                        .fuzziness("AUTO")
                )._toQuery());
            }

            List<SortOptions> sortOptions = buildSortOptions(sortBy);

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .sort(sortOptions)
                            .from((int) pageable.getOffset())
                            .size(pageable.getPageSize())
                            .trackTotalHits(t -> t.enabled(true)),
                    Map.class
            );

            List<ProductResponseDTO> results = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductResponseDTO)
                    .collect(Collectors.toList());

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;

            return new PageImpl<>(results, pageable, totalHits);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Search by shop failed", e);
            throw new RuntimeException("Elasticsearch search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, Object>> suggestProducts(String prefix, int limit) {
        checkElasticsearchAvailable();
        
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        
        try {
            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(q -> q
                                    .bool(b -> b
                                            .must(MatchQuery.of(m -> m
                                                    .field("name.autocomplete")
                                                    .query(prefix.trim())
                                            )._toQuery())
                                            .filter(TermQuery.of(t -> t
                                                    .field("is_available")
                                                    .value(true)
                                            )._toQuery())
                                    )
                            )
                            .size(limit)
                            .source(sc -> sc.filter(f -> f.includes(
                                    "id", "name", "image_urls", "effective_price", "rating"
                            ))),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> {
                        Map<String, Object> suggestion = new HashMap<>();
                        suggestion.put("id", source.get("id"));
                        suggestion.put("name", source.get("name"));
                        suggestion.put("price", source.get("effective_price"));
                        suggestion.put("rating", source.get("rating"));
                        
                        // Get first image URL
                        Object imageUrls = source.get("image_urls");
                        if (imageUrls instanceof List && !((List<?>) imageUrls).isEmpty()) {
                            suggestion.put("imageUrl", ((List<?>) imageUrls).get(0));
                        }
                        
                        return suggestion;
                    })
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Suggest failed", e);
            throw new RuntimeException("Elasticsearch suggest failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ProductResponseDTO> findSimilarProducts(Integer productId, int limit) {
        checkElasticsearchAvailable();
        
        try {
            // First, get the source product
            SearchResponse<Map> getResponse = esClient.search(s -> s
                            .index(searchIndex)
                            .query(q -> q.term(t -> t.field("id").value(productId)))
                            .size(1),
                    Map.class
            );

            if (getResponse.hits().hits().isEmpty()) {
                return List.of();
            }

            Map<String, Object> source = getResponse.hits().hits().get(0).source();
            if (source == null) {
                return List.of();
            }

            Integer categoryId = (Integer) source.get("category_id");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) source.get("tags");

            // Find similar products
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            
            // Same category
            if (categoryId != null) {
                boolQuery.must(TermQuery.of(t -> t.field("category_id").value(categoryId))._toQuery());
            }
            
            // Similar tags (boost)
            if (tags != null && !tags.isEmpty()) {
                boolQuery.should(TermsQuery.of(t -> t
                        .field("tags")
                        .terms(ts -> ts.value(tags.stream()
                                .map(FieldValue::of)
                                .collect(Collectors.toList())))
                )._toQuery());
            }

            // Exclude original product
            boolQuery.mustNot(TermQuery.of(t -> t.field("id").value(productId))._toQuery());
            
            // Must be available
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            SearchResponse<Map> response = esClient.search(s -> s
                            .index(searchIndex)
                            .query(boolQuery.build()._toQuery())
                            .sort(SortOptions.of(so -> so
                                    .field(FieldSort.of(fs -> fs.field("rating").order(SortOrder.Desc)))))
                            .size(limit),
                    Map.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(this::mapToProductResponseDTO)
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Find similar failed", e);
            throw new RuntimeException("Elasticsearch find similar failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getAvailableCities() {
        checkElasticsearchAvailable();
        
        try {
            SearchResponse<Void> response = esClient.search(s -> s
                            .index(searchIndex)
                            .size(0)
                            .query(q -> q.term(t -> t.field("is_available").value(true)))
                            .aggregations("cities", Aggregation.of(a -> a
                                    .terms(t -> t.field("shop_city").size(100))
                            )),
                    Void.class
            );

            return response.aggregations().get("cities").sterms().buckets().array().stream()
                    .map(StringTermsBucket::key)
                    .map(k -> k._get().toString())
                    .collect(Collectors.toList());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Get cities failed", e);
            throw new RuntimeException("Elasticsearch get cities failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PriceRangeStats getPriceRangeStats(String city, Integer categoryId) {
        checkElasticsearchAvailable();
        
        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(TermQuery.of(t -> t.field("is_available").value(true))._toQuery());

            if (city != null && !city.isBlank()) {
                boolQuery.filter(TermQuery.of(t -> t.field("shop_city").value(city.toLowerCase()))._toQuery());
            }
            if (categoryId != null) {
                boolQuery.filter(TermQuery.of(t -> t.field("category_id").value(categoryId))._toQuery());
            }

            SearchResponse<Void> response = esClient.search(s -> s
                            .index(searchIndex)
                            .size(0)
                            .query(boolQuery.build()._toQuery())
                            .aggregations("price_stats", Aggregation.of(a -> a
                                    .stats(st -> st.field("effective_price"))
                            )),
                    Void.class
            );

            var stats = response.aggregations().get("price_stats").stats();

            return new PriceRangeStats(
                    BigDecimal.valueOf(stats.min()),
                    BigDecimal.valueOf(stats.max()),
                    BigDecimal.valueOf(stats.avg())
            );

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("ES Get price stats failed", e);
            throw new RuntimeException("Elasticsearch get price stats failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isElasticsearchAvailable() {
        try {
            return esClient != null && esClient.ping().value();
        } catch (Exception e) {
            log.warn("Elasticsearch is not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check và throw exception nếu ES không available.
     * Gọi ở đầu mỗi search method.
     */
    private void checkElasticsearchAvailable() {
        if (!isElasticsearchAvailable()) {
            throw new RuntimeException("Elasticsearch is not available. Please ensure Elasticsearch is running on " + esHost + ":" + esPort);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String normalizeVietnamese(String text) {
        if (text == null) return "";
        text = text.replace("Đ", "D").replace("đ", "d");
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(normalized).replaceAll("").toLowerCase();
    }

    private List<SortOptions> buildSortOptions(String sortBy) {
        List<SortOptions> sortOptions = new ArrayList<>();

        if (sortBy == null || sortBy.isBlank()) {
            sortBy = "newest";
        }

        switch (sortBy.toLowerCase()) {
            case "rating_desc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("rating_score").order(SortOrder.Desc)))));
                break;
            case "rating_asc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("rating").order(SortOrder.Asc)))));
                break;
            case "price_asc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("effective_price").order(SortOrder.Asc)))));
                break;
            case "price_desc":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("effective_price").order(SortOrder.Desc)))));
                break;
            case "best_selling":
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("total_sold").order(SortOrder.Desc)))));
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("rating").order(SortOrder.Desc)))));
                break;
            case "newest":
            default:
                sortOptions.add(SortOptions.of(so -> so
                        .field(FieldSort.of(fs -> fs.field("created_at").order(SortOrder.Desc)))));
                break;
        }

        return sortOptions;
    }

    /**
     * Map ES document to ProductResponseDTO
     */
    @SuppressWarnings("unchecked")
    private ProductResponseDTO mapToProductResponseDTO(Map<String, Object> source) {
        ProductResponseDTO dto = new ProductResponseDTO();

        dto.setId(getInteger(source, "id"));
        dto.setShopId(getInteger(source, "shop_id"));
        dto.setShopName(getString(source, "shop_name"));
        dto.setShopIsOpen(true); // TODO: Calculate from opening hours
        dto.setCategoryId(getInteger(source, "category_id"));
        dto.setCategoryName(getString(source, "category_name"));
        dto.setName(getString(source, "name"));
        dto.setDescription(getString(source, "description"));
        dto.setPrice(getBigDecimal(source, "price"));
        dto.setDiscountPrice(getBigDecimal(source, "discount_price"));
        dto.setIsAvailable(getBoolean(source, "is_available"));
        dto.setPreparationTime(getInteger(source, "preparation_time"));
        dto.setStockQuantity(getInteger(source, "stock_quantity"));
        dto.setMinOrderQuantity(getInteger(source, "min_order_quantity"));
        dto.setMaxOrderQuantity(getInteger(source, "max_order_quantity"));
        dto.setRating(getDouble(source, "rating"));
        dto.setTotalReviews(getLong(source, "total_reviews"));

        // Parse arrays
        dto.setImageUrls(getStringList(source, "image_urls"));
        dto.setIngredients(getStringList(source, "ingredients_list"));
        dto.setNutritionInfo(getStringList(source, "nutrition_info"));
        dto.setTags(getStringList(source, "tags"));

        // Parse timestamps
        dto.setCreatedAt(parseDateTime(getString(source, "created_at")));
        dto.setUpdatedAt(parseDateTime(getString(source, "updated_at")));

        // Load variants from database (ES doesn't store variants)
        if (dto.getId() != null) {
            try {
                List<ProductVariant> variants = variantRepository.findActiveByProductId(dto.getId());
                dto.setVariants(variants.stream()
                        .map(this::mapToVariantResponseDTO)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                log.warn("Failed to load variants for product {}", dto.getId());
                dto.setVariants(List.of());
            }
        }

        return dto;
    }

    private VariantResponseDTO mapToVariantResponseDTO(ProductVariant variant) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(variant.getId());
        dto.setVariantTypeId(variant.getVariantType().getId());
        dto.setVariantTypeName(variant.getVariantType().getName());
        dto.setVariantValue(variant.getVariantValue());
        dto.setPriceAdjustment(variant.getPriceAdjustment());
        dto.setIsActive(variant.getIsActive());
        return dto;
    }

    // Helper methods for safe type conversion
    private Integer getInteger(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return null;
    }

    private Long getLong(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Number) return ((Number) value).longValue();
        return null;
    }

    private Double getDouble(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return null;
    }

    private BigDecimal getBigDecimal(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        return null;
    }

    private Boolean getBoolean(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof Boolean) return (Boolean) value;
        return null;
    }

    private String getString(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> getStringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) return null;
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
