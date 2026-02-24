package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ReviewDTO.*;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.*;
import com.example.FoodTourApp.service.ReviewService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final OrderItemRepository orderItemRepository;
    private final ReviewReplyRepository reviewReplyRepository;

    @Override
    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, MultipartFile[] images, User user) {
        log.info("User ID {} is creating review for {} ID {}", user.getId(), request.getReviewableType(), request.getReviewableId());

        // Validate reviewable type
        Review.ReviewableType type;
        try {
            type = Review.ReviewableType.valueOf(request.getReviewableType().toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid reviewable type. Must be 'shop' or 'product'");
        }

        // Validate shop/product tồn tại
        validateReviewable(type, request.getReviewableId());

        // Nếu review product, phải có orderId và đã mua
        // LOGIC MỚI: Review theo ORDER, không phải theo PRODUCT
        if (type == Review.ReviewableType.product) {
            if (request.getOrderId() == null) {
                throw new IllegalArgumentException("Order ID is required for product review");
            }

            // Validate order và product
            validateOrderForProductReview(request.getOrderId(), request.getReviewableId(), user.getId());

            // Kiểm tra đã review ORDER này với PRODUCT này chưa
            // Cho phép review cùng 1 product nhiều lần nếu là từ các order khác nhau
            if (reviewRepository.existsByUserIdAndOrderIdAndReviewableTypeAndReviewableId(
                    user.getId(), request.getOrderId(), type, request.getReviewableId())) {
                throw new IllegalArgumentException("Bạn đã đánh giá sản phẩm này trong đơn hàng này rồi");
            }
        } else {
            // Review shop thì check như cũ (không cần orderId)
            if (reviewRepository.existsByUserIdAndReviewableTypeAndReviewableId(
                    user.getId(), type, request.getReviewableId())) {
                throw new IllegalArgumentException("Bạn đã đánh giá shop này rồi");
            }
        }

        // Tạo review
        Review review = new Review();
        review.setUser(user);
        review.setReviewableType(type);
        review.setReviewableId(request.getReviewableId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setIsAnonymous(request.getIsAnonymous() != null ? request.getIsAnonymous() : false);
        review.setIsApproved(true); // TỰ ĐỘNG APPROVE - Review là quyền con người!

        // YÊU CẦU HOÀN TIỀN
        review.setHasRefundRequest(request.getHasRefundRequest() != null ? request.getHasRefundRequest() : false);

        if (request.getOrderId() != null) {
            Order order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new EntityNotFoundException("Order not found"));
            review.setOrder(order);
        }

        // Upload images nếu có - lưu vào thư mục ReviewImage/user_X/
        if (images != null && images.length > 0) {
            try {
                // Tạo subfolder ID dựa trên user ID - để phân biệt review của từng user
                String subfolderId = "user_" + user.getId();
                List<String> imagePaths = fileStorageService.storeFiles(images, FileStorageService.FileCategory.REVIEW_IMAGE, subfolderId);
                review.setImages(objectMapper.writeValueAsString(imagePaths));
            } catch (IOException e) {
                log.error("Error uploading review images: {}", e.getMessage());
                throw new RuntimeException("Không thể upload ảnh: " + e.getMessage());
            }
        }

        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());

        Review savedReview = reviewRepository.save(review);
        log.info("Review created successfully with ID {}", savedReview.getId());

        if (review.getHasRefundRequest() && review.getOrder() != null) {
            Order order = review.getOrder();
            order.setHasRefundRequest(true);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("Order {} flagged with refund request from review {}", order.getId(), savedReview.getId());
        }

        updateReviewableRating(type, request.getReviewableId());

        return mapToResponse(savedReview);
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Integer reviewId, UpdateReviewRequest request, MultipartFile[] images, User user) {
        log.info("User ID {} is updating review ID {}", user.getId(), reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // Chỉ owner mới được update
        if (!review.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền sửa review này");
        }

        // Update fields
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        if (request.getIsAnonymous() != null) {
            review.setIsAnonymous(request.getIsAnonymous());
        }

        // CẬP NHẬT yêu cầu hoàn tiền - CHỈ CHO PRODUCT REVIEW
        if (request.getHasRefundRequest() != null) {
            if (review.getReviewableType() == Review.ReviewableType.product) {
                review.setHasRefundRequest(request.getHasRefundRequest());
            } else {
                // Shop review không được phép có refund request
                log.warn("Attempted to set refund request on shop review ID {}", reviewId);
            }
        }

        // Upload images mới nếu có - lưu vào thư mục ReviewImage/user_X/
        if (images != null && images.length > 0) {
            // Xóa ảnh cũ
            deleteOldImages(review.getImages());

            try {
                // Tạo subfolder ID dựa trên user ID
                String subfolderId = "user_" + user.getId();
                List<String> imagePaths = fileStorageService.storeFiles(images, FileStorageService.FileCategory.REVIEW_IMAGE, subfolderId);
                review.setImages(objectMapper.writeValueAsString(imagePaths));
            } catch (IOException e) {
                log.error("Error uploading review images: {}", e.getMessage());
                throw new RuntimeException("Không thể upload ảnh: " + e.getMessage());
            }
        }

        review.setUpdatedAt(LocalDateTime.now());
        Review savedReview = reviewRepository.save(review);

        // ✅ CẬP NHẬT FLAG hasRefundRequest CHO ORDER KHI UPDATE REVIEW
        if (review.getOrder() != null) {
            Order order = review.getOrder();
            // Check xem còn review nào khác yêu cầu refund không
            boolean hasAnyRefundRequest = reviewRepository.existsByOrderIdAndHasRefundRequestTrue(order.getId());
            order.setHasRefundRequest(hasAnyRefundRequest);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            log.info("Order {} refund request flag updated to: {}", order.getId(), hasAnyRefundRequest);
        }

        // TỰ ĐỘNG CẬP NHẬT RATING VÀ TOTAL_REVIEWS CHO SHOP/PRODUCT
        updateReviewableRating(review.getReviewableType(), review.getReviewableId());

        log.info("Review ID {} updated successfully", reviewId);
        return mapToResponse(savedReview);
    }

    @Override
    @Transactional
    public void deleteReview(Integer reviewId, User user) {
        log.info("User ID {} is deleting review ID {}", user.getId(), reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // Chỉ owner mới được xóa
        if (!review.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa review này");
        }

        // Lưu lại thông tin trước khi xóa
        Review.ReviewableType type = review.getReviewableType();
        Integer reviewableId = review.getReviewableId();

        // Xóa ảnh
        deleteOldImages(review.getImages());

        reviewRepository.delete(review);

        // TỰ ĐỘNG CẬP NHẬT RATING VÀ TOTAL_REVIEWS CHO SHOP/PRODUCT SAU KHI XÓA
        updateReviewableRating(type, reviewableId);

        log.info("Review ID {} deleted successfully", reviewId);
    }

    @Override
    public Page<ReviewResponse> getReviewsByReviewable(String type, Integer id, Pageable pageable) {
        Review.ReviewableType reviewableType;
        try {
            reviewableType = Review.ReviewableType.valueOf(type.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid reviewable type. Must be 'shop' or 'product'");
        }

        // Chỉ lấy review đã approved
        Page<Review> reviews = reviewRepository.findByReviewableTypeAndReviewableIdAndIsApprovedTrue(
                reviewableType, id, pageable);

        return reviews.map(this::mapToResponse);
    }

    @Override
    public ReviewStatistics getReviewStatistics(String type, Integer id) {
        Review.ReviewableType reviewableType;
        try {
            reviewableType = Review.ReviewableType.valueOf(type.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid reviewable type. Must be 'shop' or 'product'");
        }

        ReviewStatistics stats = new ReviewStatistics();

        // Tính điểm trung bình
        Double avgRating = reviewRepository.calculateAverageRating(reviewableType, id);
        stats.setAverageRating(avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);

        // Tổng số review
        Long totalReviews = reviewRepository.countByReviewableTypeAndReviewableIdAndIsApprovedTrue(
                reviewableType, id);
        stats.setTotalReviews(totalReviews);

        // Đếm theo rating
        stats.setFiveStarCount(reviewRepository.countByRating(reviewableType, id, 5));
        stats.setFourStarCount(reviewRepository.countByRating(reviewableType, id, 4));
        stats.setThreeStarCount(reviewRepository.countByRating(reviewableType, id, 3));
        stats.setTwoStarCount(reviewRepository.countByRating(reviewableType, id, 2));
        stats.setOneStarCount(reviewRepository.countByRating(reviewableType, id, 1));

        return stats;
    }

    @Override
    public Page<ReviewResponse> getMyReviews(User user, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByUserId(user.getId(), pageable);
        return reviews.map(this::mapToResponse);
    }

    @Override
    public ReviewResponse getReviewById(Integer reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));
        return mapToResponse(review);
    }

    @Override
    @Transactional
    public ReviewResponse replyReview(Integer reviewId, ReplyRequest request, MultipartFile[] images, User user) {
        log.info("User ID {} is replying to review ID {} with {} images", user.getId(), reviewId, images != null ? images.length : 0);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // LOGIC MỚI: Conversation THREAD - cãi nhau bao nhiêu lần cũng được!
        // Mỗi lần reply tạo 1 record mới trong review_replies

        boolean isShopOwner = isShopOwner(review, user);
        boolean isReviewOwner = review.getUser().getId().equals(user.getId());

        if (!isShopOwner && !isReviewOwner) {
            throw new IllegalArgumentException("Bạn không có quyền reply review này. Chỉ chủ shop hoặc người đã review mới có thể reply.");
        }

        // Tạo reply mới trong thread
        ReviewReply reviewReply = new ReviewReply();
        reviewReply.setReview(review);
        reviewReply.setUser(user);
        reviewReply.setReplyText(request.getReply());
        reviewReply.setReplyType(isShopOwner ? ReviewReply.ReplyType.SHOP_OWNER : ReviewReply.ReplyType.USER);

        // Upload ảnh nếu có - lưu vào thư mục ReviewImage/user_X/
        if (images != null && images.length > 0) {
            try {
                String subfolderId = "user_" + user.getId();
                List<String> imagePaths = fileStorageService.storeFiles(images, FileStorageService.FileCategory.REVIEW_IMAGE, subfolderId);
                reviewReply.setImages(objectMapper.writeValueAsString(imagePaths));
                log.info("Uploaded {} images for reply", imagePaths.size());
            } catch (IOException e) {
                log.error("Error uploading reply images: {}", e.getMessage());
                throw new RuntimeException("Không thể upload ảnh: " + e.getMessage());
            }
        }

        reviewReply.setCreatedAt(LocalDateTime.now());
        reviewReply.setUpdatedAt(LocalDateTime.now());

        reviewReplyRepository.save(reviewReply);

        // Update review timestamp
        review.setUpdatedAt(LocalDateTime.now());

        // BACKWARD COMPATIBLE: Cập nhật fields cũ (reply/userReply) với reply mới nhất
        if (isShopOwner) {
            review.setReply(request.getReply());
            review.setRepliedAt(LocalDateTime.now());
            review.setRepliedBy(user);
        } else {
            review.setUserReply(request.getReply());
            review.setUserRepliedAt(LocalDateTime.now());
        }

        Review savedReview = reviewRepository.save(review);
        log.info("User ID {} replied to review ID {} successfully. Total replies: {}",
                user.getId(), reviewId, reviewReplyRepository.countByReviewId(reviewId));

        return mapToResponse(savedReview);
    }

    @Override
    public List<ReviewReplyResponse> getRepliesByReviewId(Integer reviewId) {
        log.info("Getting all replies for review ID {}", reviewId);

        // Kiểm tra review có tồn tại không
        if (!reviewRepository.existsById(reviewId)) {
            throw new EntityNotFoundException("Review not found with ID: " + reviewId);
        }

        List<ReviewReply> replies = reviewReplyRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
        return replies.stream()
                .map(this::mapReplyToResponse)
                .toList();
    }

    @Override
    @Transactional
    public ReviewReplyResponse updateReply(Integer replyId, ReplyRequest request, MultipartFile[] images, User user) {
        log.info("User ID {} is updating reply ID {}", user.getId(), replyId);

        ReviewReply reply = reviewReplyRepository.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found with ID: " + replyId));

        // Chỉ owner của reply mới được update
        if (!reply.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền sửa reply này");
        }

        // Update reply text
        reply.setReplyText(request.getReply());

        // Update images nếu có - lưu vào thư mục ReviewImage/user_X/
        if (images != null && images.length > 0) {
            // Xóa ảnh cũ
            deleteOldImages(reply.getImages());

            try {
                String subfolderId = "user_" + user.getId();
                List<String> imagePaths = fileStorageService.storeFiles(images, FileStorageService.FileCategory.REVIEW_IMAGE, subfolderId);
                reply.setImages(objectMapper.writeValueAsString(imagePaths));
                log.info("Updated {} images for reply", imagePaths.size());
            } catch (IOException e) {
                log.error("Error uploading reply images: {}", e.getMessage());
                throw new RuntimeException("Không thể upload ảnh: " + e.getMessage());
            }
        }

        reply.setUpdatedAt(LocalDateTime.now());
        ReviewReply savedReply = reviewReplyRepository.save(reply);

        log.info("Reply ID {} updated successfully", replyId);
        return mapReplyToResponse(savedReply);
    }

    @Override
    @Transactional
    public void deleteReply(Integer replyId, User user) {
        log.info("User ID {} is deleting reply ID {}", user.getId(), replyId);

        ReviewReply reply = reviewReplyRepository.findById(replyId)
                .orElseThrow(() -> new EntityNotFoundException("Reply not found with ID: " + replyId));

        // Chỉ owner của reply mới được xóa
        if (!reply.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa reply này");
        }

        // Xóa ảnh nếu có
        deleteOldImages(reply.getImages());

        reviewReplyRepository.delete(reply);
        log.info("Reply ID {} deleted successfully", replyId);
    }

    // ==================== HELPER METHODS ====================

    private void validateReviewable(Review.ReviewableType type, Integer id) {
        if (type == Review.ReviewableType.shop) {
            if (!shopRepository.existsById(id)) {
                throw new EntityNotFoundException("Shop not found with ID: " + id);
            }
        } else if (type == Review.ReviewableType.product) {
            if (!productRepository.existsById(id)) {
                throw new EntityNotFoundException("Product not found with ID: " + id);
            }
        }
    }

    private void validateOrderForProductReview(Integer orderId, Integer productId, Integer userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found"));

        // Kiểm tra order thuộc về user
        if (!order.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Order không thuộc về bạn");
        }

        // Kiểm tra order đã hoàn thành (delivered)
        if (order.getOrderStatus() != Order.OrderStatus.delivered) {
            throw new IllegalArgumentException("Chỉ có thể đánh giá đơn hàng đã hoàn thành");
        }

        // Kiểm tra product có trong order không
        boolean hasProduct = orderItemRepository.existsByOrderIdAndProductId(orderId, productId);
        if (!hasProduct) {
            throw new IllegalArgumentException("Sản phẩm không có trong đơn hàng này");
        }
    }

    private void deleteOldImages(String imagesJson) {
        if (imagesJson != null && !imagesJson.isEmpty()) {
            try {
                List<String> imagePaths = objectMapper.readValue(imagesJson, new TypeReference<List<String>>() {});
                for (String path : imagePaths) {
                    fileStorageService.deleteFile(path);
                }
            } catch (JsonProcessingException e) {
                log.error("Error parsing images JSON: {}", e.getMessage());
            }
        }
    }

    private ReviewResponse mapToResponse(Review review) {
        ReviewResponse response = new ReviewResponse();
        response.setId(review.getId());
        response.setUserId(review.getUser().getId());

        // Nếu anonymous thì không hiện thông tin user
        if (review.getIsAnonymous()) {
            response.setUserFullName("Người dùng ẩn danh");
            response.setUserAvatarUrl(null);
        } else {
            response.setUserFullName(review.getUser().getFullName());
            response.setUserAvatarUrl(review.getUser().getAvatarUrl());
        }

        response.setReviewableType(review.getReviewableType().toString());
        response.setReviewableId(review.getReviewableId());

        // Lấy tên shop/product và ảnh
        response.setReviewableName(getReviewableName(review.getReviewableType(), review.getReviewableId()));
        response.setReviewableImageUrls(getReviewableImageUrls(review.getReviewableType(), review.getReviewableId()));

        response.setOrderId(review.getOrder() != null ? review.getOrder().getId() : null);
        response.setRating(review.getRating());
        response.setComment(review.getComment());

        // Parse images JSON
        if (review.getImages() != null && !review.getImages().isEmpty()) {
            try {
                List<String> images = objectMapper.readValue(review.getImages(), new TypeReference<List<String>>() {});
                response.setImages(images);
            } catch (JsonProcessingException e) {
                log.error("Error parsing images JSON: {}", e.getMessage());
                response.setImages(new ArrayList<>());
            }
        } else {
            response.setImages(new ArrayList<>());
        }

        response.setIsAnonymous(review.getIsAnonymous());
        response.setReply(review.getReply()); // Shop owner reply
        response.setRepliedAt(review.getRepliedAt());

        if (review.getRepliedBy() != null) {
            response.setRepliedByName(review.getRepliedBy().getFullName());
        }

        // User reply lại shop (phản bác) - DEPRECATED nhưng giữ lại
        response.setUserReply(review.getUserReply());
        response.setUserRepliedAt(review.getUserRepliedAt());

        // NEW: Load conversation thread (tất cả replies)
        List<ReviewReply> replies = reviewReplyRepository.findByReviewIdOrderByCreatedAtAsc(review.getId());
        response.setReplies(replies.stream()
                .map(this::mapReplyToResponse)
                .toList());

        // YÊU CẦU HOÀN TIỀN
        response.setHasRefundRequest(review.getHasRefundRequest());

        response.setCreatedAt(review.getCreatedAt());
        response.setUpdatedAt(review.getUpdatedAt());

        return response;
    }

    private ReviewReplyResponse mapReplyToResponse(ReviewReply reply) {
        ReviewReplyResponse response = new ReviewReplyResponse();
        response.setId(reply.getId());
        response.setReviewId(reply.getReview().getId());
        response.setUserId(reply.getUser().getId());
        response.setUserFullName(reply.getUser().getFullName());
        response.setUserAvatarUrl(reply.getUser().getAvatarUrl());
        response.setReplyText(reply.getReplyText());

        // Parse images JSON
        if (reply.getImages() != null && !reply.getImages().isEmpty()) {
            try {
                List<String> images = objectMapper.readValue(reply.getImages(), new TypeReference<List<String>>() {});
                response.setImages(images);
            } catch (JsonProcessingException e) {
                log.error("Error parsing reply images JSON: {}", e.getMessage());
                response.setImages(new ArrayList<>());
            }
        } else {
            response.setImages(new ArrayList<>());
        }

        response.setReplyType(reply.getReplyType().toString());
        response.setCreatedAt(reply.getCreatedAt());
        response.setUpdatedAt(reply.getUpdatedAt());
        return response;
    }

    private String getReviewableName(Review.ReviewableType type, Integer id) {
        try {
            if (type == Review.ReviewableType.shop) {
                return shopRepository.findById(id)
                        .map(Shop::getShopName)
                        .orElse("Unknown Shop");
            } else {
                return productRepository.findById(id)
                        .map(Product::getName)
                        .orElse("Unknown Product");
            }
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private List<String> getReviewableImageUrls(Review.ReviewableType type, Integer id) {
        try {
            if (type == Review.ReviewableType.shop) {
                return shopRepository.findById(id)
                        .map(shop -> {
                            if (shop.getLogoUrl() != null && !shop.getLogoUrl().isEmpty()) {
                                return List.of(shop.getLogoUrl());
                            }
                            return List.<String>of();
                        })
                        .orElse(List.of());
            } else {
                return productRepository.findById(id)
                        .map(product -> {
                            if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                                return Arrays.asList(product.getImageUrls().split(","));
                            }
                            return List.<String>of();
                        })
                        .orElse(List.of());
            }
        } catch (Exception e) {
            log.error("Error getting reviewable image URLs: {}", e.getMessage());
            return List.of();
        }
    }

    private void validateReplyPermission(Review review, User user) {
        // Lấy thông tin shop từ reviewable
        Shop shop = null;

        if (review.getReviewableType() == Review.ReviewableType.shop) {
            // Review shop -> check shop owner
            shop = shopRepository.findById(review.getReviewableId())
                    .orElseThrow(() -> new EntityNotFoundException("Shop not found"));
        } else if (review.getReviewableType() == Review.ReviewableType.product) {
            // Review product -> check product's shop owner
            Product product = productRepository.findById(review.getReviewableId())
                    .orElseThrow(() -> new EntityNotFoundException("Product not found"));
            shop = product.getShop();
        }

        // Kiểm tra user có phải là seller (owner) của shop không
        if (shop == null || !shop.getSeller().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Bạn không có quyền reply review này. Chỉ chủ shop mới có thể reply.");
        }
    }

    private boolean isShopOwner(Review review, User user) {
        try {
            Shop shop = null;

            if (review.getReviewableType() == Review.ReviewableType.shop) {
                shop = shopRepository.findById(review.getReviewableId()).orElse(null);
            } else if (review.getReviewableType() == Review.ReviewableType.product) {
                Product product = productRepository.findById(review.getReviewableId()).orElse(null);
                if (product != null) {
                    shop = product.getShop();
                }
            }

            return shop != null && shop.getSeller().getId().equals(user.getId());
        } catch (Exception e) {
            log.error("Error checking shop owner: {}", e.getMessage());
            return false;
        }
    }

    private void updateReviewableRating(Review.ReviewableType type, Integer reviewableId) {
        try {
            if (type == Review.ReviewableType.shop) {
                // Cập nhật cho shop
                Shop shop = shopRepository.findById(reviewableId)
                        .orElseThrow(() -> new EntityNotFoundException("Shop not found"));

                // Tính toán lại rating và totalReviews
                ReviewStatistics stats = getReviewStatistics("shop", reviewableId);
                shop.setRating(stats.getAverageRating());
                shop.setTotalReviews(stats.getTotalReviews());

                shopRepository.save(shop);
                log.info("Updated rating for shop ID {}", reviewableId);
            } else if (type == Review.ReviewableType.product) {
                // Cập nhật cho product
                Product product = productRepository.findById(reviewableId)
                        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

                // Tính toán lại rating và totalReviews
                ReviewStatistics stats = getReviewStatistics("product", reviewableId);
                product.setRating(stats.getAverageRating());
                product.setTotalReviews(stats.getTotalReviews());

                productRepository.save(product);
                log.info("Updated rating for product ID {}", reviewableId);
            }
        } catch (Exception e) {
            log.error("Error updating reviewable rating: {}", e.getMessage());
        }
    }
}
