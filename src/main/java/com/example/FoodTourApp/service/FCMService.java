package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.User;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Service gửi Firebase Cloud Messaging (FCM) push notification.
 */
public interface FCMService {

    void sendToUser(User user, String title, String body, Map<String, String> data);

    // ── Chat ─────────────────────────────────────────────────────────────────
    void sendChatMessageNotification(User recipient, String senderName, String senderAvatar,
                                     Long conversationId, String content, String messageType);

    // ── Wallet ───────────────────────────────────────────────────────────────
    void sendWalletTransactionNotification(User user, BigDecimal amount, BigDecimal newBalance,
                                           String transactionType, String description);

    // ── Order (Buyer) ─────────────────────────────────────────────────────────
    void sendOrderCreatedNotification(User buyer, String orderNumber, String shopName, BigDecimal totalAmount);

    void sendOrderPaidNotification(User buyer, String orderNumber, String shopName, BigDecimal totalAmount);

    void sendOrderStatusChangedToBuyer(User buyer, String orderNumber, String shopName,
                                       String newStatus, String extraInfo);

    // ── Order (Seller) ────────────────────────────────────────────────────────
    void sendNewOrderToSeller(User seller, String orderNumber, String buyerName, BigDecimal totalAmount);

    void sendOrderStatusChangedToSeller(User seller, String orderNumber, String buyerName,
                                        String newStatus, String extraInfo);

    // ── Review ────────────────────────────────────────────────────────────────
    void sendNewReviewToSeller(User seller, String reviewerName, String targetName,
                               int rating, String orderNumber);

    void sendReviewReplyNotification(User recipient, String replierName, String replierRole,
                                     String targetName, Integer reviewId);

    // ── Shop Approval ─────────────────────────────────────────────────────────
    void sendShopApprovedNotification(User seller, String shopName);

    void sendShopRejectedNotification(User seller, String shopName, String reason);

    // ── Seller Approval ───────────────────────────────────────────────────────
    void sendSellerApprovedNotification(User user);

    void sendSellerRejectedNotification(User user, String reason);

    // ── Voucher ───────────────────────────────────────────────────────────────
    void sendVoucherCreatedNotification(User recipient, String voucherTitle, String code, String discountSummary);
}

