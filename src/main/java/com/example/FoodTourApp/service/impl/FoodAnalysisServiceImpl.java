package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisMessageItem;
import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisRequest;
import com.example.FoodTourApp.DTO.FoodAnalysisDTO.FoodAnalysisResponse;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.FoodAnalysisMessageRepository;
import com.example.FoodTourApp.repository.FoodAnalysisSessionRepository;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.repository.ReviewRepository;
import com.example.FoodTourApp.service.FoodAnalysisAI;
import com.example.FoodTourApp.service.FoodAnalysisService;
import com.example.FoodTourApp.service.MessageEncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FoodAnalysisServiceImpl implements FoodAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FoodAnalysisServiceImpl.class);

    private static final int MEMORY_WINDOW = 10;
    private static final int MAX_REVIEWS = 15;

    private final ProductRepository productRepo;
    private final ReviewRepository reviewRepo;
    private final FoodAnalysisSessionRepository sessionRepo;
    private final FoodAnalysisMessageRepository messageRepo;
    private final ChatLanguageModel chatModel;
    private final MessageEncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Cache key = sessionKey (UUID).
     * - 300 phiên đồng thời tối đa
     * - Inactive 30 phút → evict
     * - Cache miss → restore lịch sử từ DB
     */
    private final Cache<String, FoodAnalysisAI> sessionCache = Caffeine.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(300)
            .build();

    @Autowired
    public FoodAnalysisServiceImpl(ProductRepository productRepo,
                                   ReviewRepository reviewRepo,
                                   FoodAnalysisSessionRepository sessionRepo,
                                   FoodAnalysisMessageRepository messageRepo,
                                   ChatLanguageModel chatModel,
                                   MessageEncryptionService encryptionService) {
        this.productRepo = productRepo;
        this.reviewRepo = reviewRepo;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
        this.chatModel = chatModel;
        this.encryptionService = encryptionService;
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FoodAnalysisResponse chat(User user, Integer productId, FoodAnalysisRequest request) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy món ăn id=" + productId));

        // Lấy hoặc tạo session cho user × product này
        boolean[] isNew = {false};
        FoodAnalysisSession session = sessionRepo
                .findByUser_IdAndProduct_Id(user.getId(), productId)
                .orElseGet(() -> {
                    isNew[0] = true;
                    FoodAnalysisSession s = new FoodAnalysisSession();
                    s.setUser(user);
                    s.setProduct(product);
                    s.setSessionKey(UUID.randomUUID().toString());
                    return sessionRepo.save(s);
                });

        // Lưu tin user (encrypt trước khi lưu DB)
        FoodAnalysisMessage userMsg = new FoodAnalysisMessage();
        userMsg.setSenderType("USER");
        userMsg.setContent(encryptionService.encrypt(request.getContent()));
        session.addMessage(userMsg);
        messageRepo.save(userMsg);

        // Lấy AI instance
        FoodAnalysisAI ai = getOrCreateAI(session, isNew[0]);

        // Tin đầu tiên: kèm full context; tin tiếp theo: câu hỏi thuần
        String messageToAI = isNew[0]
                ? buildFirstMessage(product, request.getContent())
                : request.getContent();

        String reply = callAI(ai, session.getSessionKey(), messageToAI);

        // Lưu tin bot (encrypt trước khi lưu DB)
        FoodAnalysisMessage botMsg = new FoodAnalysisMessage();
        botMsg.setSenderType("BOT");
        botMsg.setContent(encryptionService.encrypt(reply));
        session.addMessage(botMsg);
        messageRepo.save(botMsg);

        log.info("[FoodAnalysis] user={} product={} session={}... → {} chars",
                user.getId(), productId, session.getSessionKey().substring(0, 8), reply.length());

        return FoodAnalysisResponse.builder()
                .reply(reply)
                .sessionId(session.getSessionKey())
                .createdAt(botMsg.getCreatedAt())
                .build();
    }

    @Override
    public List<FoodAnalysisMessageItem> getHistory(User user, Integer productId) {
        return sessionRepo.findByUser_IdAndProduct_Id(user.getId(), productId)
                .map(session -> session.getMessages().stream()
                        .map(msg -> FoodAnalysisMessageItem.builder()
                                .id(msg.getId())
                                .senderType(msg.getSenderType())
                                .content(encryptionService.decrypt(msg.getContent()))
                                .createdAt(msg.getCreatedAt())
                                .build())
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    @Override
    @Transactional
    public void deleteSession(User user, Integer productId) {
        sessionRepo.findByUser_IdAndProduct_Id(user.getId(), productId)
                .ifPresent(session -> {
                    sessionCache.invalidate(session.getSessionKey());
                    sessionRepo.delete(session);
                    log.info("[FoodAnalysis] user={} xóa session product={}", user.getId(), productId);
                });
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private FoodAnalysisAI getOrCreateAI(FoodAnalysisSession session, boolean isNewSession) {
        return sessionCache.get(session.getSessionKey(), key -> {
            ChatMemory memory = MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW);

            if (!isNewSession) {
                // Cache miss: reload lịch sử từ DB vào memory
                // CRITICAL: Decrypt content trước khi đưa vào AI memory
                // Nếu không decrypt, AI nhận ciphertext Base64 → mất toàn bộ context
                List<FoodAnalysisMessage> history =
                        messageRepo.findBySession_IdOrderByCreatedAtAsc(session.getId());
                int start = Math.max(0, history.size() - MEMORY_WINDOW);
                for (int i = start; i < history.size(); i++) {
                    FoodAnalysisMessage m = history.get(i);
                    String decryptedContent = encryptionService.decrypt(m.getContent());
                    if ("USER".equals(m.getSenderType())) {
                        memory.add(UserMessage.from(decryptedContent));
                    } else {
                        memory.add(AiMessage.from(decryptedContent));
                    }
                }
                log.info("[FoodAnalysis] Cache miss — restored {} msgs session {}...",
                        history.size() - start, key.substring(0, 8));
            }

            return AiServices.builder(FoodAnalysisAI.class)
                    .chatLanguageModel(chatModel)
                    .chatMemory(memory)
                    .build();
        });
    }

    private String buildFirstMessage(Product product, String userQuestion) {
        return buildProductContext(product)
                + "\n\n" + buildShopContext(product.getShop())
                + "\n\n" + buildReviewsContext(product.getId())
                + "\n\n---\nCâu hỏi của người dùng: " + userQuestion;
    }

    private String buildProductContext(Product product) {
        StringBuilder sb = new StringBuilder("[PRODUCT_INFO]\n");
        sb.append("Tên món: ").append(product.getName()).append("\n");
        if (notBlank(product.getDescription()))
            sb.append("Mô tả: ").append(product.getDescription()).append("\n");
        sb.append("Giá gốc: ").append(formatPrice(product.getPrice())).append(" VNĐ");
        if (product.hasDiscount())
            sb.append(" | Giá KM: ").append(formatPrice(product.getDiscountPrice())).append(" VNĐ");
        sb.append("\n");
        sb.append("Đánh giá: ").append(product.getRating()).append("/5.0")
                .append(" (").append(product.getTotalReviews()).append(" đánh giá)\n");
        if (product.getPreparationTime() != null)
            sb.append("Thời gian chuẩn bị: ").append(product.getPreparationTime()).append(" phút\n");
        if (product.getCategory() != null && notBlank(product.getCategory().getName()))
            sb.append("Danh mục: ").append(product.getCategory().getName()).append("\n");

        String[] ingredients = parseJsonArray(product.getIngredients());
        if (ingredients.length > 0)
            sb.append("Nguyên liệu: ").append(String.join(", ", ingredients)).append("\n");

        String[] nutrition = parseJsonArray(product.getNutritionInfo());
        if (nutrition.length > 0)
            sb.append("Dinh dưỡng: ").append(String.join(" | ", nutrition)).append("\n");

        String[] tags = parseJsonArray(product.getTags());
        if (tags.length > 0)
            sb.append("Tags: ").append(String.join(", ", tags)).append("\n");

        sb.append("[/PRODUCT_INFO]");
        return sb.toString();
    }

    private String buildShopContext(Shop shop) {
        if (shop == null) return "[SHOP_INFO]\nKhông có thông tin quán.\n[/SHOP_INFO]";
        StringBuilder sb = new StringBuilder("[SHOP_INFO]\n");
        sb.append("Tên quán: ").append(shop.getShopName()).append("\n");
        if (notBlank(shop.getAddressLine()))
            sb.append("Địa chỉ: ").append(shop.getAddressLine()).append("\n");
        if (notBlank(shop.getWard()))
            sb.append("Phường/Xã: ").append(shop.getWard()).append("\n");
        if (notBlank(shop.getDistrict()))
            sb.append("Quận/Huyện: ").append(shop.getDistrict()).append("\n");
        if (notBlank(shop.getCity()))
            sb.append("Thành phố: ").append(shop.getCity()).append("\n");
        if (notBlank(shop.getPhone()))
            sb.append("Điện thoại: ").append(shop.getPhone()).append("\n");
        if (notBlank(shop.getOpeningHours()))
            sb.append("Giờ mở cửa: ").append(shop.getOpeningHours()).append("\n");
        sb.append("[/SHOP_INFO]");
        return sb.toString();
    }

    private String buildReviewsContext(Integer productId) {
        List<Review> reviews = reviewRepo.findTopApprovedReviewsForProduct(
                productId, PageRequest.of(0, MAX_REVIEWS));
        if (reviews.isEmpty())
            return "[REVIEWS]\nChưa có đánh giá nào.\n[/REVIEWS]";

        StringBuilder sb = new StringBuilder("[REVIEWS]\n");
        sb.append(reviews.size()).append(" đánh giá gần nhất:\n\n");
        for (int i = 0; i < reviews.size(); i++) {
            Review r = reviews.get(i);
            boolean anon = Boolean.TRUE.equals(r.getIsAnonymous());
            String author = anon ? "Ẩn danh"
                    : (r.getUser() != null && notBlank(r.getUser().getFullName())
                    ? r.getUser().getFullName() : "Người dùng");
            sb.append(i + 1).append(". ").append(author)
                    .append(" — ").append(r.getRating()).append("★\n");
            if (notBlank(r.getComment()))
                sb.append("   \"").append(truncate(r.getComment(), 300)).append("\"\n");
        }
        sb.append("[/REVIEWS]");
        return sb.toString();
    }

    private String callAI(FoodAnalysisAI ai, String sessionKey, String message) {
        try {
            return ai.analyze(message);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("503") || msg.contains("UNAVAILABLE") || msg.contains("high demand")) {
                log.warn("[FoodAnalysis] Gemini 503 session {}...", sessionKey.substring(0, 8));
                return "Xin lỗi, hệ thống AI đang bận. Vui lòng thử lại sau vài giây nhé! 🙏";
            }
            if (msg.contains("429") || msg.contains("RESOURCE_EXHAUSTED") || msg.contains("quota")) {
                log.warn("[FoodAnalysis] Gemini rate limit session {}...", sessionKey.substring(0, 8));
                return "Bạn đang hỏi quá nhanh! Chờ một chút rồi thử lại nhé 😊";
            }
            log.error("[FoodAnalysis] AI error session {}...: {}", sessionKey.substring(0, 8), msg, e);
            return "Xin lỗi, mình gặp sự cố kết nối. Vui lòng thử lại sau!";
        }
    }

    private String[] parseJsonArray(String json) {
        if (json == null || json.isBlank()) return new String[0];
        try { return objectMapper.readValue(json, String[].class); }
        catch (Exception e) { return new String[0]; }
    }

    private String formatPrice(java.math.BigDecimal p) {
        return p == null ? "?" : String.format("%,.0f", p);
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private String truncate(String t, int max) {
        if (t == null) return "";
        return t.length() <= max ? t : t.substring(0, max) + "...";
    }
}
