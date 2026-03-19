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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatbotMessageServiceImpl implements ChatbotMessageService {

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

        // Lưu tin bot
        ChatbotMessage botMsg = new ChatbotMessage();
        botMsg.setConversation(conv);
        botMsg.setSenderType("BOT");
        botMsg.setSenderName("FoodTour Bot");
        botMsg.setContent(botReply);
        messageRepo.save(botMsg);
        conv.addMessage(botMsg);

        // Extract product IDs từ RAG retrieval
        List<Integer> relevantProductIds = foodVectorService.retrieveProductIds(request.getContent());
        
        // Tạo navigation URLs
        List<String> navigationUrls = relevantProductIds.stream()
                .map(id -> "product_detail/" + id)
                .collect(Collectors.toList());
        
        // Tạo danh sách sản phẩm gợi ý với đầy đủ thông tin
        List<ChatbotMessageResponse.ProductSuggestion> suggestedProducts = new ArrayList<>();
        for (Integer productId : relevantProductIds) {
            productRepository.findById(productId).ifPresent(product -> {
                // Chỉ thêm products có isAvailable = true
                if (product.getIsAvailable() != null && product.getIsAvailable()) {
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
                    
                    // Lấy ảnh đầu tiên từ imageUrls JSON array
                    String imageUrl = extractFirstImageUrl(product.getImageUrls());
                    suggestion.setImageUrl(imageUrl);
                    
                    suggestion.setNavigationUrl("product_detail/" + product.getId());
                    
                    suggestedProducts.add(suggestion);
                }
            });
        }

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

        return conv.getMessages().stream()
                .map(msg -> {
                    ChatbotMessageResponse response = new ChatbotMessageResponse();
                    response.setId(msg.getId());
                    response.setSenderType(msg.getSenderType());
                    response.setSenderName(msg.getSenderName());
                    response.setContent(msg.getContent());
                    response.setCreatedAt(msg.getCreatedAt());
                    // Note: historical messages don't have navigation URLs and suggested products
                    // Only new messages include these fields
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
