package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.UserBehavior;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.UserBehaviorRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.UserBehaviorBufferService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis List làm write buffer cho UserBehavior.
 *
 * Flow:
 *   HTTP request → buffer() → RPUSH Redis List → return (fast)
 *   @Scheduled 30s → flush() → LRANGE + batch saveAll → LTRIM
 *
 * Redis key: "user:behavior:buffer" (List)
 * Mỗi item: JSON string của các fields cần thiết
 *
 * Race-condition safety:
 *   - Chỉ có 1 scheduled flush chạy tại 1 thời điểm (fixedDelay)
 *   - RPUSH (right) + LRANGE(0,N) + LTRIM(N,-1): an toàn với concurrent RPUSH
 *     vì LTRIM dựa trên index snapshot, không ảnh hưởng items mới push vào
 */
@Service
@Slf4j
public class UserBehaviorBufferServiceImpl implements UserBehaviorBufferService {

    private static final String BUFFER_KEY   = "user:behavior:buffer";
    private static final int    FLUSH_BATCH  = 500;   // tối đa 500 records/flush
    private static final int    RETENTION_DAYS = 90; // giữ hành vi 90 ngày

    private final RedisTemplate<String, Object> redisObjectTemplate;
    private final UserBehaviorRepository behaviorRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    public UserBehaviorBufferServiceImpl(
            @Qualifier("redisObjectTemplate") RedisTemplate<String, Object> redisObjectTemplate,
            UserBehaviorRepository behaviorRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ObjectMapper objectMapper) {
        this.redisObjectTemplate = redisObjectTemplate;
        this.behaviorRepository  = behaviorRepository;
        this.userRepository      = userRepository;
        this.productRepository   = productRepository;
        this.objectMapper        = objectMapper;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // WRITE - buffered (non-blocking)
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void buffer(Integer userId, Integer productId,
                       UserBehavior.ActionType actionType,
                       double weight,
                       String sessionId,
                       UserBehavior.BehaviorSource source,
                       Integer viewDurationSeconds,
                       Integer quantity,
                       Integer rating,
                       Integer categoryId,
                       Integer shopId) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("userId",               userId);
            data.put("productId",            productId);
            data.put("actionType",           actionType.name());
            data.put("weight",               weight);
            data.put("sessionId",            sessionId);
            data.put("source",               source != null ? source.name() : null);
            data.put("viewDurationSeconds",  viewDurationSeconds);
            data.put("quantity",             quantity);
            data.put("rating",               rating);
            data.put("categoryId",           categoryId);
            data.put("shopId",               shopId);

            String json = objectMapper.writeValueAsString(data);
            // RPUSH: thêm vào TAIL → flush đọc từ HEAD (FIFO)
            redisObjectTemplate.opsForList().rightPush(BUFFER_KEY, json);

        } catch (Exception e) {
            log.error("⚠️ Redis buffer failed, falling back to direct MySQL save: {}", e.getMessage());
            fallbackDirectSave(userId, productId, actionType, weight, sessionId, source,
                    viewDurationSeconds, quantity, rating, categoryId, shopId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FLUSH - batch write to MySQL every 30s
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Scheduled(fixedDelay = 30_000) // 30 giây sau khi flush trước hoàn thành
    @Transactional
    public int flush() {
        try {
            Long size = redisObjectTemplate.opsForList().size(BUFFER_KEY);
            if (size == null || size == 0) return 0;

            long toProcess = Math.min(size, FLUSH_BATCH);

            // Đọc từ HEAD (oldest first → FIFO)
            List<Object> items = redisObjectTemplate.opsForList()
                    .range(BUFFER_KEY, 0, toProcess - 1);
            if (items == null || items.isEmpty()) return 0;

            List<UserBehavior> behaviors = new ArrayList<>(items.size());
            for (Object item : items) {
                try {
                    Map<String, Object> data = objectMapper.readValue(
                            (String) item, new TypeReference<>() {});
                    UserBehavior b = deserialize(data);
                    if (b != null) behaviors.add(b);
                } catch (Exception e) {
                    log.warn("⚠️ Skipping malformed buffer item: {}", e.getMessage());
                }
            }

            if (!behaviors.isEmpty()) {
                behaviorRepository.saveAll(behaviors);
            }

            // LTRIM: xóa toProcess items đầu (đã xử lý)
            // LTRIM key N -1 → giữ từ index N đến cuối
            // Items mới RPUSH vào TAIL trong lúc flush → index tăng → không bị trim
            redisObjectTemplate.opsForList().trim(BUFFER_KEY, toProcess, -1);

            if (!behaviors.isEmpty()) {
                log.debug("✅ Flushed {} behaviors from Redis buffer → MySQL", behaviors.size());
            }
            return behaviors.size();

        } catch (Exception e) {
            log.error("❌ Failed to flush behavior buffer: {}", e.getMessage(), e);
            return 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DATA RETENTION - xóa behaviors cũ hơn 90 ngày (3 giờ sáng)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Giữ UserBehavior tối đa 90 ngày:
     * - 14 ngày: real-time embedding computation
     * - 30 ngày: precompute embeddings job
     * - 6 tháng: training export (RecommendationDataExportService dùng 6 tháng)
     * → 90 ngày là sweet spot: đủ cho training, không phình DB vô hạn
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldBehaviors() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
            behaviorRepository.deleteByCreatedAtBefore(cutoff);
            log.info("🗑️ Cleaned up UserBehavior records older than {} days", RETENTION_DAYS);
        } catch (Exception e) {
            log.warn("⚠️ Failed to cleanup old behaviors: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private UserBehavior deserialize(Map<String, Object> data) {
        try {
            Integer userId    = toInt(data.get("userId"));
            Integer productId = toInt(data.get("productId"));
            String  actionStr = (String) data.get("actionType");
            double  weight    = ((Number) data.get("weight")).doubleValue();
            String  sessionId = (String) data.get("sessionId");
            String  sourceStr = (String) data.get("source");
            Integer viewDur   = toInt(data.get("viewDurationSeconds"));
            Integer quantity  = toInt(data.get("quantity"));
            Integer rating    = toInt(data.get("rating"));
            Integer categoryId = toInt(data.get("categoryId"));
            Integer shopId    = toInt(data.get("shopId"));

            // getReferenceById: tạo proxy không query DB → efficient trong batch
            User    userRef    = userRepository.getReferenceById(userId);
            Product productRef = productRepository.getReferenceById(productId);

            return UserBehavior.builder()
                    .user(userRef)
                    .product(productRef)
                    .actionType(UserBehavior.ActionType.valueOf(actionStr))
                    .weight(weight)
                    .sessionId(sessionId)
                    .source(sourceStr != null ? UserBehavior.BehaviorSource.valueOf(sourceStr) : null)
                    .viewDurationSeconds(viewDur)
                    .quantity(quantity)
                    .rating(rating)
                    .categoryId(categoryId)
                    .shopId(shopId)
                    // createdAt được set bởi @PrePersist → tối đa lệch 30s so với thực tế
                    // 30s lag không ảnh hưởng embedding (time decay dùng đơn vị ngày)
                    .build();

        } catch (Exception e) {
            log.warn("⚠️ Failed to deserialize buffered behavior: {}", e.getMessage());
            return null;
        }
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        return ((Number) val).intValue();
    }

    /**
     * Fallback khi Redis không available: ghi thẳng MySQL để không mất data.
     */
    private void fallbackDirectSave(Integer userId, Integer productId,
                                     UserBehavior.ActionType actionType, double weight,
                                     String sessionId, UserBehavior.BehaviorSource source,
                                     Integer viewDurationSeconds, Integer quantity, Integer rating,
                                     Integer categoryId, Integer shopId) {
        try {
            UserBehavior behavior = UserBehavior.builder()
                    .user(userRepository.getReferenceById(userId))
                    .product(productRepository.getReferenceById(productId))
                    .actionType(actionType)
                    .weight(weight)
                    .sessionId(sessionId)
                    .source(source)
                    .viewDurationSeconds(viewDurationSeconds)
                    .quantity(quantity)
                    .rating(rating)
                    .categoryId(categoryId)
                    .shopId(shopId)
                    .build();
            behaviorRepository.save(behavior);
        } catch (Exception ex) {
            log.error("❌ Fallback direct save also failed: {}", ex.getMessage());
        }
    }
}
