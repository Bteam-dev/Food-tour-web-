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

        // Kiểm tra đã review chưa
        if (reviewRepository.existsByUserIdAndReviewableTypeAndReviewableId(
                user.getId(), type, request.getReviewableId())) {
            throw new IllegalArgumentException("Bạn đã đánh giá " + type + " này rồi");
        }

        // Validate shop/product tồn tại
        validateReviewable(type, request.getReviewableId());

        // Nếu review product, phải có orderId và đã mua
        if (type == Review.ReviewableType.product) {
            if (request.getOrderId() == null) {
                throw new IllegalArgumentException("Order ID is required for product review");
            }
            validateOrderForProductReview(request.getOrderId(), request.getReviewableId(), user.getId());
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

        // Xóa ảnh
        deleteOldImages(review.getImages());

        reviewRepository.delete(review);
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
    public ReviewResponse replyReview(Integer reviewId, ReplyRequest request, User user) {
        log.info("User ID {} is replying to review ID {}", user.getId(), reviewId);

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found"));

        // AI ĐĂNG NHẬP CŨNG REPLY ĐƯỢC - Giống Facebook!
        // Không cần check quyền gì cả

        review.setReply(request.getReply());
        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(user);
        review.setUpdatedAt(LocalDateTime.now());

        Review savedReview = reviewRepository.save(review);
        log.info("User ID {} replied to review ID {} successfully", user.getId(), reviewId);

        return mapToResponse(savedReview);
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

        // Lấy tên shop/product
        response.setReviewableName(getReviewableName(review.getReviewableType(), review.getReviewableId()));

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
        response.setReply(review.getReply());
        response.setRepliedAt(review.getRepliedAt());

        if (review.getRepliedBy() != null) {
            response.setRepliedByName(review.getRepliedBy().getFullName());
        }

        response.setCreatedAt(review.getCreatedAt());
        response.setUpdatedAt(review.getUpdatedAt());

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
}
