package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.FCMService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class FCMServiceImpl implements FCMService {

    private static final NumberFormat VND_FORMAT = NumberFormat.getInstance(new Locale("vi", "VN"));

    private boolean isFirebaseAvailable() {
        return !FirebaseApp.getApps().isEmpty();
    }

    private String formatVND(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return VND_FORMAT.format(amount) + " ₫";
    }

    @Override
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
    // CHAT NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendChatMessageNotification(User recipient, String senderName, String senderAvatar,
                                            Long conversationId, String content, String messageType) {
        String title = "💬 " + senderName;
        String body;
        switch (messageType == null ? "TEXT" : messageType.toUpperCase()) {
            case "IMAGE" -> body = "📷 Đã gửi một ảnh";
            case "VIDEO" -> body = "🎥 Đã gửi một video";
            case "AUDIO" -> body = "🎵 Đã gửi một tin nhắn thoại";
            case "FILE"  -> body = "📎 Đã gửi một tệp đính kèm";
            default      -> body = (content != null && content.length() > 100)
                    ? content.substring(0, 100) + "…"
                    : (content != null ? content : "");
        }

        sendToUser(recipient, title, body, Map.of(
                "type", "NEW_CHAT_MESSAGE",
                "conversationId", conversationId.toString(),
                "senderName", senderName != null ? senderName : "",
                "senderAvatar", senderAvatar != null ? senderAvatar : "",
                "messageType", messageType != null ? messageType : "TEXT"
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WALLET NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    @Override
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

    private String shortOrderNumber(String orderNumber) {
        if (orderNumber == null) return "";
        return orderNumber.length() > 8
                ? orderNumber.substring(orderNumber.length() - 8).toUpperCase()
                : orderNumber.toUpperCase();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHOP APPROVAL NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendShopApprovedNotification(User seller, String shopName) {
        String title = "✅ Shop của bạn đã được duyệt";
        String body = "Shop \"" + shopName + "\" đã được admin phê duyệt và có thể bắt đầu hoạt động!";
        sendToUser(seller, title, body, Map.of(
                "type", "SHOP_APPROVED",
                "shopName", shopName != null ? shopName : ""
        ));
    }

    @Override
    @Async
    public void sendShopRejectedNotification(User seller, String shopName, String reason) {
        String title = "❌ Shop của bạn bị từ chối";
        String body = "Shop \"" + shopName + "\" đã bị admin từ chối."
                + (reason != null && !reason.isBlank() ? " Lý do: " + reason : "");
        sendToUser(seller, title, body, Map.of(
                "type", "SHOP_REJECTED",
                "shopName", shopName != null ? shopName : "",
                "reason", reason != null ? reason : ""
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SELLER APPROVAL NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendSellerApprovedNotification(User user) {
        String title = "✅ Đăng ký người bán đã được duyệt";
        String body = "Chúc mừng! Đơn đăng ký trở thành người bán của bạn đã được admin phê duyệt. Bạn có thể bắt đầu tạo cửa hàng ngay bây giờ!";
        sendToUser(user, title, body, Map.of(
                "type", "SELLER_APPROVED"
        ));
    }

    @Override
    @Async
    public void sendSellerRejectedNotification(User user, String reason) {
        String title = "❌ Đăng ký người bán bị từ chối";
        String body = "Đơn đăng ký người bán của bạn đã bị từ chối."
                + (reason != null && !reason.isBlank() ? " Lý do: " + reason : "");
        sendToUser(user, title, body, Map.of(
                "type", "SELLER_REJECTED",
                "reason", reason != null ? reason : ""
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VOUCHER NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Async
    public void sendVoucherCreatedNotification(User recipient, String voucherTitle, String code, String discountSummary) {
        String title = "🎁 Có voucher mới dành cho bạn!";
        String body = voucherTitle + " – Mã: " + code
                + (discountSummary != null && !discountSummary.isBlank() ? " (" + discountSummary + ")" : "");
        sendToUser(recipient, title, body, Map.of(
                "type", "VOUCHER_CREATED",
                "code", code != null ? code : "",
                "title", voucherTitle != null ? voucherTitle : ""
        ));
    }
}

