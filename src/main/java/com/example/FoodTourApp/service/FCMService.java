package com.example.FoodTourApp.service;

import com.example.FoodTourApp.entity.User;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FCMService {

    private static final NumberFormat VND_FORMAT = NumberFormat.getInstance(new Locale("vi", "VN"));

    private boolean isFirebaseAvailable() {
        return !FirebaseApp.getApps().isEmpty();
    }

    private String formatVND(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return VND_FORMAT.format(amount) + " ₫";
    }

    /**
     * Gửi push notification đến một user cụ thể
     */
    @Async
    public void sendToUser(User user, String title, String body, Map<String, String> data) {
        if (!isFirebaseAvailable()) {
            log.debug("Firebase not available. Skipping push notification for user {}", user.getId());
            return;
        }

        String token = user.getFcmToken();
        if (token == null || token.isBlank()) {
            log.debug("User {} has no FCM token. Skipping push notification.", user.getId());
            return;
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data != null ? data : Map.of())
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setSound("default")
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build());

            String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
            log.info("Push notification sent to user {} successfully. Message ID: {}", user.getId(), response);
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                    || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                log.warn("FCM token for user {} is invalid/unregistered. Will be cleaned up next login.", user.getId());
            } else {
                log.error("Failed to send push notification to user {}: {}", user.getId(), e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WALLET NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ví: số dư thay đổi (tiền vào hoặc tiền ra)
     */
    @Async
    public void sendWalletTransactionNotification(User user, BigDecimal amount, BigDecimal newBalance,
                                                  String transactionType, String description) {
        boolean isCredit = amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
        String sign = isCredit ? "+" : "";
        String emoji = isCredit ? "💰" : "💸";

        String title = emoji + " Biến động số dư ví";
        String body = sign + formatVND(amount) + " • Số dư: " + formatVND(newBalance) + "\n" + description;

        sendToUser(user, title, body, Map.of(
                "type", "WALLET_TRANSACTION",
                "transactionType", transactionType,
                "amount", amount != null ? amount.toPlainString() : "0",
                "newBalance", newBalance != null ? newBalance.toPlainString() : "0"
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORDER NOTIFICATIONS (BUYER)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Người mua: đặt hàng thành công (đơn hàng mới tạo)
     */
    @Async
    public void sendOrderCreatedNotification(User buyer, String orderNumber, String shopName, BigDecimal totalAmount) {
        String title = "🛒 Đặt hàng thành công";
        String body = "Đơn hàng #" + shortOrderNumber(orderNumber) + " tại " + shopName
                + " • " + formatVND(totalAmount);

        sendToUser(buyer, title, body, Map.of(
                "type", "ORDER_CREATED",
                "orderNumber", orderNumber
        ));
    }

    /**
     * Người mua: thanh toán thành công
     */
    @Async
    public void sendOrderPaidNotification(User buyer, String orderNumber, String shopName, BigDecimal totalAmount) {
        String title = "✅ Thanh toán thành công";
        String body = "Đơn hàng #" + shortOrderNumber(orderNumber) + " tại " + shopName
                + " đã được thanh toán " + formatVND(totalAmount);

        sendToUser(buyer, title, body, Map.of(
                "type", "ORDER_PAID",
                "orderNumber", orderNumber
        ));
    }

    /**
     * Người mua: trạng thái đơn hàng thay đổi (seller xác nhận / đã giao / hủy / hoàn tiền)
     */
    @Async
    public void sendOrderStatusChangedToBuyer(User buyer, String orderNumber, String shopName,
                                              String newStatus, String extraInfo) {
        String title;
        String body;

        switch (newStatus.toLowerCase()) {
            case "confirmed" -> {
                title = "📦 Đơn hàng đã được xác nhận";
                body = "Shop " + shopName + " đã xác nhận đơn #" + shortOrderNumber(orderNumber)
                        + " và đang chuẩn bị hàng.";
            }
            case "delivered" -> {
                title = "🎉 Đơn hàng đã giao thành công";
                body = "Đơn #" + shortOrderNumber(orderNumber) + " từ " + shopName
                        + " đã được giao. Hãy đánh giá để giúp shop cải thiện nhé!";
            }
            case "cancelled" -> {
                title = "❌ Đơn hàng đã bị hủy";
                body = "Đơn #" + shortOrderNumber(orderNumber) + " từ " + shopName + " đã bị hủy."
                        + (extraInfo != null && !extraInfo.isBlank() ? " Lý do: " + extraInfo : "");
            }
            case "refunded" -> {
                title = "↩️ Hoàn tiền thành công";
                body = "Đơn #" + shortOrderNumber(orderNumber) + ": tiền đã được hoàn về ví của bạn.";
            }
            default -> {
                title = "🔔 Cập nhật đơn hàng";
                body = "Đơn #" + shortOrderNumber(orderNumber) + " từ " + shopName
                        + " vừa được cập nhật trạng thái.";
            }
        }

        sendToUser(buyer, title, body, Map.of(
                "type", "ORDER_STATUS_CHANGED",
                "orderNumber", orderNumber,
                "newStatus", newStatus
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ORDER NOTIFICATIONS (SELLER)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Seller: có đơn hàng mới
     */
    @Async
    public void sendNewOrderToSeller(User seller, String orderNumber, String buyerName, BigDecimal totalAmount) {
        String title = "🔔 Bạn có đơn hàng mới!";
        String body = "Khách " + buyerName + " vừa đặt đơn #" + shortOrderNumber(orderNumber)
                + " • " + formatVND(totalAmount) + ". Xác nhận ngay!";

        sendToUser(seller, title, body, Map.of(
                "type", "NEW_ORDER",
                "orderNumber", orderNumber
        ));
    }

    /**
     * Seller: trạng thái đơn hàng thay đổi (người mua hủy / hoàn tiền)
     */
    @Async
    public void sendOrderStatusChangedToSeller(User seller, String orderNumber, String buyerName,
                                               String newStatus, String extraInfo) {
        String title;
        String body;

        switch (newStatus.toLowerCase()) {
            case "cancelled" -> {
                title = "❌ Đơn hàng bị hủy bởi khách";
                body = "Khách " + buyerName + " đã hủy đơn #" + shortOrderNumber(orderNumber) + "."
                        + (extraInfo != null && !extraInfo.isBlank() ? " Lý do: " + extraInfo : "");
            }
            case "refunded" -> {
                title = "↩️ Đơn hàng đã được hoàn tiền";
                body = "Đơn #" + shortOrderNumber(orderNumber) + " của " + buyerName
                        + " đã được xử lý hoàn tiền.";
            }
            default -> {
                title = "🔔 Cập nhật đơn hàng";
                body = "Đơn #" + shortOrderNumber(orderNumber) + " của " + buyerName
                        + " vừa được cập nhật.";
            }
        }

        sendToUser(seller, title, body, Map.of(
                "type", "ORDER_STATUS_CHANGED_SELLER",
                "orderNumber", orderNumber,
                "newStatus", newStatus
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REVIEW NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Seller: nhận được đánh giá mới
     */
    @Async
    public void sendNewReviewToSeller(User seller, String reviewerName, String targetName,
                                      int rating, String orderNumber) {
        String stars = "⭐".repeat(Math.max(1, Math.min(rating, 5)));
        String title = "⭐ Bạn có đánh giá mới";
        String body = reviewerName + " vừa đánh giá " + stars + " cho " + targetName
                + (orderNumber != null ? " (Đơn #" + shortOrderNumber(orderNumber) + ")" : "");

        sendToUser(seller, title, body, Map.of(
                "type", "NEW_REVIEW",
                "rating", String.valueOf(rating),
                "targetName", targetName
        ));
    }

    /**
     * Thông báo khi có người reply trong thread đánh giá
     * - Gửi cho review owner (người viết đánh giá) nếu shop reply
     * - Gửi cho shop owner nếu user reply lại
     * - KHÔNG gửi thông báo cho chính người vừa reply
     */
    @Async
    public void sendReviewReplyNotification(User recipient, String replierName, String replierRole,
                                            String targetName, Integer reviewId) {
        String title;
        String body;

        if ("SHOP_OWNER".equals(replierRole)) {
            title = "💬 Shop đã phản hồi đánh giá của bạn";
            body = "Shop " + replierName + " vừa trả lời đánh giá của bạn về " + targetName;
        } else {
            title = "💬 Khách hàng phản hồi đánh giá";
            body = replierName + " vừa trả lời trong đánh giá về " + targetName;
        }

        sendToUser(recipient, title, body, Map.of(
                "type", "REVIEW_REPLY",
                "reviewId", reviewId != null ? reviewId.toString() : "",
                "replierName", replierName
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Rút ngắn order number cho dễ đọc (lấy 8 ký tự cuối UUID)
     */
    private String shortOrderNumber(String orderNumber) {
        if (orderNumber == null) return "";
        return orderNumber.length() > 8
                ? orderNumber.substring(orderNumber.length() - 8).toUpperCase()
                : orderNumber.toUpperCase();
    }
}

