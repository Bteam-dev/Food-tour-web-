package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ChatbotDTO.ChatbotMessageResponse;
import com.example.FoodTourApp.DTO.ChatbotDTO.SendChatbotMessageRequest;
import com.example.FoodTourApp.entity.ChatbotConversation;
import com.example.FoodTourApp.entity.ChatbotMessage;
import com.example.FoodTourApp.entity.Product;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ChatbotConversationRepository;
import com.example.FoodTourApp.repository.ChatbotMessageRepository;
import com.example.FoodTourApp.repository.ProductRepository;
import com.example.FoodTourApp.service.ChatbotMessageService;
import com.example.FoodTourApp.service.ChatbotAIService;
import com.example.FoodTourApp.service.FoodVectorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatbotMessageServiceImpl implements ChatbotMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatbotMessageServiceImpl.class);

    private final ChatbotConversationRepository conversationRepo;
    private final ChatbotMessageRepository messageRepo;
    private final ChatbotAIService chatbotAIService;
    private final FoodVectorService foodVectorService;
    private final ProductRepository productRepository;

    @Autowired
    public ChatbotMessageServiceImpl(ChatbotConversationRepository conversationRepo,
                                     ChatbotMessageRepository messageRepo,
                                     ChatbotAIService chatbotAIService,
                                     FoodVectorService foodVectorService,
                                     ProductRepository productRepository) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.chatbotAIService = chatbotAIService;
        this.foodVectorService = foodVectorService;
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public ChatbotMessageResponse sendMessage(User user, Long conversationId, SendChatbotMessageRequest request) {
        ChatbotConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        String userName = user.getFullName() != null ? user.getFullName() : user.getUsername();

        // Lưu tin user
        ChatbotMessage userMsg = new ChatbotMessage();
        userMsg.setConversation(conv);
        userMsg.setSenderType("USER");
        userMsg.setSenderName(userName);
        userMsg.setContent(request.getContent());
        messageRepo.save(userMsg);
        conv.addMessage(userMsg);

        // Gọi AI (RAG) — truyền conversationId để memory độc lập per conversation
        String botReply = chatbotAIService.generateReply(conversationId, request.getContent());

        // Extract product IDs từ RAG retrieval
        // RAG engine (Elasticsearch + Vector Similarity) đã tự động tìm products phù hợp với query:
        // - Hỏi tên món → tìm món có tên giống/chứa từ khóa
        // - Hỏi danh mục → tìm món trong category đó
        // - Hỏi nhu cầu nâng cao (trời lạnh/nóng, healthy, cay...) → tìm món có tags/description phù hợp
        // - Hỏi về quán → tìm món của quán đó (theo shopId)
        // - Hỏi về giá/rating/thời gian → tìm món match điều kiện
        // Vector similarity đảm bảo suggestions LUÔN tương quan với query của user
        List<Integer> relevantProductIds = foodVectorService.retrieveProductIds(request.getContent());

        log.info("📋 Query: '{}' → Found {} relevant products: {}",
                request.getContent(), relevantProductIds.size(), relevantProductIds);

        if (relevantProductIds.isEmpty()) {
            log.warn("⚠️ WARNING: RAG returned EMPTY results → LLM might hallucinate!");
        }

        // Tạo navigation URLs (giữ nguyên thứ tự từ RAG - món match nhất ở đầu)
        List<String> navigationUrls = relevantProductIds.stream()
                .map(id -> "product_detail/" + id)
                .collect(Collectors.toList());

        // Tạo danh sách sản phẩm gợi ý với đầy đủ thông tin
        List<ChatbotMessageResponse.ProductSuggestion> suggestedProducts = new ArrayList<>();

        Map<Integer, Product> productMap = new java.util.LinkedHashMap<>();
        productRepository.findAllById(relevantProductIds).forEach(p -> productMap.put(p.getId(), p));

        for (Integer productId : relevantProductIds) {
            Product product = productMap.get(productId);
            if (product != null && product.getIsAvailable() != null && product.getIsAvailable()) {
                ChatbotMessageResponse.ProductSuggestion suggestion =
                        new ChatbotMessageResponse.ProductSuggestion();
                suggestion.setProductId(product.getId());
                suggestion.setProductName(product.getName());
                suggestion.setShopName(product.getShop().getShopName());
                suggestion.setShopId(product.getShop().getId());
                suggestion.setPrice(product.getPrice());
                suggestion.setDiscountPrice(product.getDiscountPrice());
                suggestion.setRating(product.getRating());
                suggestion.setTotalReviews(product.getTotalReviews());
                suggestion.setImageUrl(extractFirstImageUrl(product.getImageUrls()));
                suggestion.setNavigationUrl("product_detail/" + product.getId());
                suggestedProducts.add(suggestion);

                log.info("  ✅ Added suggestion #{}: {} (shopId: {}, score rank: {})",
                        suggestedProducts.size(), product.getName(), product.getShop().getId(),
                        relevantProductIds.indexOf(productId) + 1);
            }
        }

        // Lưu tin bot (một lần duy nhất, đã có đủ navigation + products)
        ChatbotMessage botMsg = new ChatbotMessage();
        botMsg.setConversation(conv);
        botMsg.setSenderType("BOT");
        botMsg.setSenderName("FoodTour Bot");
        botMsg.setContent(botReply);

        ObjectMapper mapper = new ObjectMapper();
        try {
            if (!navigationUrls.isEmpty()) {
                botMsg.setNavigationUrlsJson(mapper.writeValueAsString(navigationUrls));
            }
            if (!suggestedProducts.isEmpty()) {
                botMsg.setSuggestedProductsJson(mapper.writeValueAsString(suggestedProducts));
            }
        } catch (Exception e) {
            log.warn("[Chatbot] Không thể serialize navigation/products: {}", e.getMessage());
        }

        messageRepo.save(botMsg);
        conv.addMessage(botMsg);

        ChatbotMessageResponse response = new ChatbotMessageResponse();
        response.setId(botMsg.getId());
        response.setSenderType("BOT");
        response.setSenderName("FoodTour Bot");
        response.setContent(botReply);
        response.setCreatedAt(botMsg.getCreatedAt());
        response.setNavigationUrls(navigationUrls);
        response.setSuggestedProducts(suggestedProducts);
        return response;
    }

    @Override
    public List<ChatbotMessageResponse> getConversationMessages(Long conversationId, User user) {
        ChatbotConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Not found"));
        if (!conv.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        ObjectMapper mapper = new ObjectMapper();
        return conv.getMessages().stream()
                .map(msg -> {
                    ChatbotMessageResponse response = new ChatbotMessageResponse();
                    response.setId(msg.getId());
                    response.setSenderType(msg.getSenderType());
                    response.setSenderName(msg.getSenderName());
                    response.setContent(msg.getContent());
                    response.setCreatedAt(msg.getCreatedAt());

                    // Deserialize navigation URLs từ DB
                    if (msg.getNavigationUrlsJson() != null) {
                        try {
                            List<String> urls = mapper.readValue(
                                    msg.getNavigationUrlsJson(),
                                    mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                            response.setNavigationUrls(urls);
                        } catch (Exception e) {
                            log.warn("[Chatbot] Không thể deserialize navigationUrls cho message {}", msg.getId());
                        }
                    }

                    // Deserialize suggested products từ DB
                    if (msg.getSuggestedProductsJson() != null) {
                        try {
                            List<ChatbotMessageResponse.ProductSuggestion> products = mapper.readValue(
                                    msg.getSuggestedProductsJson(),
                                    mapper.getTypeFactory().constructCollectionType(
                                            List.class, ChatbotMessageResponse.ProductSuggestion.class));
                            response.setSuggestedProducts(products);
                        } catch (Exception e) {
                            log.warn("[Chatbot] Không thể deserialize suggestedProducts cho message {}", msg.getId());
                        }
                    }

                    return response;
                }).collect(Collectors.toList());
    }
    
    /**
     * Extract first image URL from JSON array string
     * Input: "[\"url1\",\"url2\"]" or null
     * Output: "url1" or null
     */
    private String extractFirstImageUrl(String imageUrlsJson) {
        if (imageUrlsJson == null || imageUrlsJson.trim().isEmpty()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            String[] urls = mapper.readValue(imageUrlsJson, String[].class);
            return urls.length > 0 ? urls[0] : null;
        } catch (Exception e) {
            return null;
        }
    }
}
