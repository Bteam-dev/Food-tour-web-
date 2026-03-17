package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.WalletDTO.WalletResponseDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletTransactionResponseDTO;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.WalletTransaction;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.repository.WalletTransactionRepository;
import com.example.FoodTourApp.service.FCMService;
import com.example.FoodTourApp.service.WalletService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ═══════════════════════════════════════════════════════════════
 * LUỒNG TIỀN:
 *
 * ── Wallet (app_wallet) ──────────────────────────────────────
 *  payOrder          : buyer  → (escrow_hold)  → admin wallet
 *  markDelivered     : admin  → (escrow_release)→ seller (88%)
 *                      admin giữ lại 12% commission
 *  cancelOrder (trước delivered) :
 *                      admin wallet → (refund) → buyer
 *                      seller KHÔNG bị động
 *  refundOrder (sau delivered) :
 *                      seller wallet → (88%)   → buyer
 *                      admin  wallet → (12%)   → buyer
 *
 * ── COD (ship_cod) ───────────────────────────────────────────
 *  markDelivered     : trừ ví seller 12% commission → admin
 *                      (seller nhận tiền mặt từ shipper, nộp commission)
 *  refundOrder (sau delivered) :
 *                      admin wallet → (12%)    → buyer
 *                      seller wallet→ (88%)    → buyer
 *                      (buyer được hoàn đủ từ 2 nguồn)
 * ═══════════════════════════════════════════════════════════════
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("12.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final FCMService fcmService;

    // ─────────────────────────────────────────────────────────────────────────
    // QUERY
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public WalletResponseDTO getWalletInfo(User user) {
        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<WalletTransaction> recentTransactions = walletTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .limit(10)
                .collect(Collectors.toList());

        WalletResponseDTO response = new WalletResponseDTO();
        response.setUserId(dbUser.getId());
        response.setFullName(dbUser.getFullName());
        response.setBalance(dbUser.getWalletBalance());
        response.setRecentTransactions(
                recentTransactions.stream()
                        .map(this::convertToTransactionDTO)
                        .collect(Collectors.toList())
        );
        return response;
    }

    @Override
    public Page<WalletTransactionResponseDTO> getTransactionHistory(User user, Pageable pageable) {
        log.info("Getting transaction history for user: {} with pagination", user.getId());
        return walletTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId(), pageable)
                .map(this::convertToTransactionDTO);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // WALLET PAYMENT — buyer thanh toán, tiền vào admin escrow
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void processOrderPayment(Order order) {
        log.info("processOrderPayment: order={}", order.getId());

        BigDecimal total = order.getTotalAmount();

        // 1. Trừ buyer
        User dbBuyer = loadUser(order.getUser().getId());
        if (dbBuyer.getWalletBalance().compareTo(total) < 0) {
            throw new RuntimeException(
                "Số dư ví không đủ. Cần: " + total + " VND, Có: " + dbBuyer.getWalletBalance() + " VND");
        }
        BigDecimal buyerBefore = dbBuyer.getWalletBalance();
        BigDecimal buyerAfter  = buyerBefore.subtract(total);
        dbBuyer.setWalletBalance(buyerAfter);
        dbBuyer.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbBuyer);

        recordTx(dbBuyer, WalletTransaction.TransactionType.payment,
                 total.negate(), buyerBefore, buyerAfter, order,
                 "Thanh toán đơn hàng #" + order.getOrderNumber());

        fcmService.sendWalletTransactionNotification(dbBuyer, total.negate(), buyerAfter,
                "payment", "Thanh toán đơn hàng #" + order.getOrderNumber());

        // 2. Cộng toàn bộ totalAmount vào admin wallet (escrow – giữ hộ cho seller)
        User admin = loadAdmin();
        BigDecimal adminBefore = admin.getWalletBalance();
        BigDecimal adminAfter  = adminBefore.add(total);
        admin.setWalletBalance(adminAfter);
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        recordTx(admin, WalletTransaction.TransactionType.escrow_hold,
                 total, adminBefore, adminAfter, order,
                 "Giữ escrow đơn #" + order.getOrderNumber()
                     + " (Shop: " + order.getShop().getShopName() + ")");

        // 3. Tính commission và ghi vào Order (để dùng cho bước release sau)
        BigDecimal rate = resolveCommissionRate(order);
        BigDecimal commission = calcCommission(total, rate);
        BigDecimal sellerReceive = total.subtract(commission);

        order.setPlatformCommissionRate(rate);
        order.setPlatformCommissionAmount(commission);
        order.setSellerReceivedAmount(sellerReceive);
        order.setPaymentStatus(Order.PaymentStatus.paid);
        order.setUpdatedAt(LocalDateTime.now());

        log.info("Order {} escrow held. Buyer paid: {}, Seller will receive: {}, Commission: {}",
                order.getId(), total, sellerReceive, commission);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RELEASE ESCROW — sau khi giao thành công (wallet)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void releaseEscrowToSeller(Order order) {
        log.info("releaseEscrowToSeller: order={}", order.getId());

        BigDecimal commission   = order.getPlatformCommissionAmount();
        BigDecimal sellerReceive = order.getSellerReceivedAmount();

        if (commission == null || sellerReceive == null) {
            log.warn("Order {} missing commission data, recalculating", order.getId());
            BigDecimal rate = resolveCommissionRate(order);
            commission   = calcCommission(order.getTotalAmount(), rate);
            sellerReceive = order.getTotalAmount().subtract(commission);
            order.setPlatformCommissionRate(rate);
            order.setPlatformCommissionAmount(commission);
            order.setSellerReceivedAmount(sellerReceive);
        }

        // 1. Trừ admin: giải phóng phần seller (admin vẫn giữ commission)
        User admin = loadAdmin();
        BigDecimal adminBefore = admin.getWalletBalance();
        BigDecimal adminAfter  = adminBefore.subtract(sellerReceive);
        admin.setWalletBalance(adminAfter);
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        recordTx(admin, WalletTransaction.TransactionType.escrow_release,
                 sellerReceive.negate(), adminBefore, adminAfter, order,
                 "Giải phóng escrow → seller, đơn #" + order.getOrderNumber());

        // 2. Cộng seller
        User dbSeller = loadUser(order.getShop().getSeller().getId());
        BigDecimal sellerBefore = dbSeller.getWalletBalance();
        BigDecimal sellerAfter  = sellerBefore.add(sellerReceive);
        dbSeller.setWalletBalance(sellerAfter);
        dbSeller.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbSeller);

        recordTx(dbSeller, WalletTransaction.TransactionType.received_payment,
                 sellerReceive, sellerBefore, sellerAfter, order,
                 "Nhận tiền đơn #" + order.getOrderNumber()
                     + " (đã trừ hoa hồng " + order.getPlatformCommissionRate() + "%)");

        fcmService.sendWalletTransactionNotification(dbSeller, sellerReceive, sellerAfter,
                "received_payment", "Nhận tiền đơn #" + order.getOrderNumber());

        log.info("Order {} escrow released. Seller received: {}, Admin keeps commission: {}",
                order.getId(), sellerReceive, commission);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COD COMMISSION — thu commission sau khi giao xong
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void processCommissionCOD(Order order) {
        log.info("processCommissionCOD: order={}", order.getId());

        BigDecimal rate = resolveCommissionRate(order);
        BigDecimal commission   = calcCommission(order.getTotalAmount(), rate);
        BigDecimal sellerReceive = order.getTotalAmount().subtract(commission);

        order.setPlatformCommissionRate(rate);
        order.setPlatformCommissionAmount(commission);
        order.setSellerReceivedAmount(sellerReceive);

        // 1. Trừ commission từ ví seller (có thể âm → debt tracking)
        User dbSeller = loadUser(order.getShop().getSeller().getId());
        BigDecimal sellerBefore = dbSeller.getWalletBalance();
        BigDecimal sellerAfter  = sellerBefore.subtract(commission);
        dbSeller.setWalletBalance(sellerAfter);
        dbSeller.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbSeller);

        recordTx(dbSeller, WalletTransaction.TransactionType.platform_commission,
                 commission.negate(), sellerBefore, sellerAfter, order,
                 "Phí hoa hồng " + rate + "% đơn COD #" + order.getOrderNumber());

        if (sellerAfter.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Seller {} ví âm sau khi thu hoa hồng COD. Balance: {}", dbSeller.getId(), sellerAfter);
        }

        fcmService.sendWalletTransactionNotification(dbSeller, commission.negate(), sellerAfter,
                "platform_commission",
                "Phí hoa hồng " + rate + "% đơn COD #" + order.getOrderNumber());

        // 2. Cộng commission vào admin wallet
        User admin = loadAdmin();
        BigDecimal adminBefore = admin.getWalletBalance();
        BigDecimal adminAfter  = adminBefore.add(commission);
        admin.setWalletBalance(adminAfter);
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        recordTx(admin, WalletTransaction.TransactionType.platform_commission,
                 commission, adminBefore, adminAfter, order,
                 "Hoa hồng " + rate + "% đơn COD #" + order.getOrderNumber()
                     + " (Shop: " + order.getShop().getShopName() + ")");

        log.info("Order {} COD commission processed. Commission: {}, Seller balance: {}",
                order.getId(), commission, sellerAfter);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REFUND — hủy trước khi giao (pending / confirmed)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void refundCancelledOrder(Order order) {
        log.info("refundCancelledOrder: order={}", order.getId());

        if (order.getPaymentMethod() != Order.PaymentMethod.app_wallet) {
            // COD chưa chuyển tiền qua ví → không cần hoàn
            log.info("Order {} is COD, no wallet refund needed for cancellation", order.getId());
            return;
        }
        if (order.getPaymentStatus() != Order.PaymentStatus.paid) {
            log.warn("Order {} not paid, skip refund", order.getId());
            return;
        }

        BigDecimal total = order.getTotalAmount();

        // 1. Trừ admin escrow (hoàn lại phần đã giữ hộ)
        User admin = loadAdmin();
        BigDecimal adminBefore = admin.getWalletBalance();
        BigDecimal adminAfter  = adminBefore.subtract(total);
        admin.setWalletBalance(adminAfter);
        admin.setUpdatedAt(LocalDateTime.now());
        userRepository.save(admin);

        recordTx(admin, WalletTransaction.TransactionType.refund,
                 total.negate(), adminBefore, adminAfter, order,
                 "Hoàn escrow đơn bị hủy #" + order.getOrderNumber());

        // 2. Cộng buyer
        User dbBuyer = loadUser(order.getUser().getId());
        BigDecimal buyerBefore = dbBuyer.getWalletBalance();
        BigDecimal buyerAfter  = buyerBefore.add(total);
        dbBuyer.setWalletBalance(buyerAfter);
        dbBuyer.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbBuyer);

        recordTx(dbBuyer, WalletTransaction.TransactionType.refund,
                 total, buyerBefore, buyerAfter, order,
                 "Hoàn tiền đơn hủy #" + order.getOrderNumber());

        fcmService.sendWalletTransactionNotification(dbBuyer, total, buyerAfter,
                "refund", "Hoàn tiền đơn hủy #" + order.getOrderNumber());

        order.setPaymentStatus(Order.PaymentStatus.refunded);
        order.setUpdatedAt(LocalDateTime.now());

        log.info("Order {} cancellation refund completed. Buyer received: {}", order.getId(), total);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REFUND — sau khi đã delivered (seller đã nhận tiền)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void refundDeliveredOrder(Order order) {
        log.info("refundDeliveredOrder: order={}", order.getId());

        BigDecimal commission   = order.getPlatformCommissionAmount();
        BigDecimal sellerReceive = order.getSellerReceivedAmount();
        BigDecimal total        = order.getTotalAmount();

        if (commission == null || sellerReceive == null) {
            BigDecimal rate = resolveCommissionRate(order);
            commission   = calcCommission(total, rate);
            sellerReceive = total.subtract(commission);
        }

        if (order.getPaymentMethod() == Order.PaymentMethod.app_wallet) {
            // ── Wallet: seller đã nhận sellerReceive, admin đã giữ commission ──

            // 1. Trừ seller
            User dbSeller = loadUser(order.getShop().getSeller().getId());
            BigDecimal sellerBefore = dbSeller.getWalletBalance();
            BigDecimal sellerAfter  = sellerBefore.subtract(sellerReceive);
            dbSeller.setWalletBalance(sellerAfter);
            dbSeller.setUpdatedAt(LocalDateTime.now());
            userRepository.save(dbSeller);

            recordTx(dbSeller, WalletTransaction.TransactionType.refund,
                     sellerReceive.negate(), sellerBefore, sellerAfter, order,
                     "Hoàn tiền đơn #" + order.getOrderNumber() + " (đã delivered)");

            fcmService.sendWalletTransactionNotification(dbSeller, sellerReceive.negate(), sellerAfter,
                    "refund", "Hoàn tiền đơn #" + order.getOrderNumber());

            if (sellerAfter.compareTo(BigDecimal.ZERO) < 0) {
                log.warn("Seller {} ví âm sau refund. Balance: {}", dbSeller.getId(), sellerAfter);
            }

            // 2. Trừ admin commission
            User admin = loadAdmin();
            BigDecimal adminBefore = admin.getWalletBalance();
            BigDecimal adminAfter  = adminBefore.subtract(commission);
            admin.setWalletBalance(adminAfter);
            admin.setUpdatedAt(LocalDateTime.now());
            userRepository.save(admin);

            recordTx(admin, WalletTransaction.TransactionType.commission_refund,
                     commission.negate(), adminBefore, adminAfter, order,
                     "Hoàn hoa hồng đơn #" + order.getOrderNumber());

            // 3. Cộng buyer toàn bộ
            User dbBuyer = loadUser(order.getUser().getId());
            BigDecimal buyerBefore = dbBuyer.getWalletBalance();
            BigDecimal buyerAfter  = buyerBefore.add(total);
            dbBuyer.setWalletBalance(buyerAfter);
            dbBuyer.setUpdatedAt(LocalDateTime.now());
            userRepository.save(dbBuyer);

            recordTx(dbBuyer, WalletTransaction.TransactionType.refund,
                     total, buyerBefore, buyerAfter, order,
                     "Hoàn tiền đơn #" + order.getOrderNumber());

            fcmService.sendWalletTransactionNotification(dbBuyer, total, buyerAfter,
                    "refund", "Hoàn tiền đơn #" + order.getOrderNumber());

        } else {
            // ── COD: seller nhận tiền mặt (88%), admin đã thu commission từ ví seller (12%)
            //         → hoàn: trừ seller 88% từ ví, trừ admin 12%, cộng buyer toàn bộ ──

            // 1. Trừ seller 88%
            User dbSeller = loadUser(order.getShop().getSeller().getId());
            BigDecimal sellerBefore = dbSeller.getWalletBalance();
            BigDecimal sellerAfter  = sellerBefore.subtract(sellerReceive);
            dbSeller.setWalletBalance(sellerAfter);
            dbSeller.setUpdatedAt(LocalDateTime.now());
            userRepository.save(dbSeller);

            recordTx(dbSeller, WalletTransaction.TransactionType.refund,
                     sellerReceive.negate(), sellerBefore, sellerAfter, order,
                     "Hoàn tiền COD đơn #" + order.getOrderNumber() + " (đã delivered)");

            fcmService.sendWalletTransactionNotification(dbSeller, sellerReceive.negate(), sellerAfter,
                    "refund", "Hoàn tiền đơn COD #" + order.getOrderNumber());

            // 2. Trừ admin 12%
            User admin = loadAdmin();
            BigDecimal adminBefore = admin.getWalletBalance();
            BigDecimal adminAfter  = adminBefore.subtract(commission);
            admin.setWalletBalance(adminAfter);
            admin.setUpdatedAt(LocalDateTime.now());
            userRepository.save(admin);

            recordTx(admin, WalletTransaction.TransactionType.commission_refund,
                     commission.negate(), adminBefore, adminAfter, order,
                     "Hoàn hoa hồng COD đơn #" + order.getOrderNumber());

            // 3. Cộng buyer
            User dbBuyer = loadUser(order.getUser().getId());
            BigDecimal buyerBefore = dbBuyer.getWalletBalance();
            BigDecimal buyerAfter  = buyerBefore.add(total);
            dbBuyer.setWalletBalance(buyerAfter);
            dbBuyer.setUpdatedAt(LocalDateTime.now());
            userRepository.save(dbBuyer);

            recordTx(dbBuyer, WalletTransaction.TransactionType.refund,
                     total, buyerBefore, buyerAfter, order,
                     "Hoàn tiền đơn COD #" + order.getOrderNumber());

            fcmService.sendWalletTransactionNotification(dbBuyer, total, buyerAfter,
                    "refund", "Hoàn tiền đơn COD #" + order.getOrderNumber());
        }

        order.setPaymentStatus(Order.PaymentStatus.refunded);
        order.setUpdatedAt(LocalDateTime.now());

        log.info("Order {} delivered-refund completed. Buyer received: {}", order.getId(), total);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEPRECATED — giữ lại để không break compile, delegate sang method mới
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Deprecated
    @Transactional
    public void refundOrder(Order order) {
        // Delegate: nếu order chưa delivered → refundCancelledOrder
        // Nếu đã delivered → refundDeliveredOrder
        if (order.getOrderStatus() == Order.OrderStatus.delivered) {
            refundDeliveredOrder(order);
        } else {
            refundCancelledOrder(order);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private User loadUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
    }

    private User loadAdmin() {
        return userRepository.findFirstByRoleName(Role.RoleName.ADMIN)
                .orElseThrow(() -> new RuntimeException("Admin account not found in system"));
    }

    private BigDecimal resolveCommissionRate(Order order) {
        return (order.getPlatformCommissionRate() != null)
                ? order.getPlatformCommissionRate()
                : COMMISSION_RATE;
    }

    private BigDecimal calcCommission(BigDecimal amount, BigDecimal rate) {
        return amount.multiply(rate).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private void recordTx(User user, WalletTransaction.TransactionType type,
                          BigDecimal amount, BigDecimal before, BigDecimal after,
                          Order order, String description) {
        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceBefore(before);
        tx.setBalanceAfter(after);
        tx.setOrder(order);
        tx.setDescription(description);
        tx.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(tx);
    }

    private WalletTransactionResponseDTO convertToTransactionDTO(WalletTransaction transaction) {
        WalletTransactionResponseDTO dto = new WalletTransactionResponseDTO();
        dto.setId(transaction.getId());
        dto.setUserId(transaction.getUser().getId());
        dto.setUserFullName(transaction.getUser().getFullName());
        dto.setTransactionType(transaction.getTransactionType().name());
        dto.setAmount(transaction.getAmount());
        dto.setBalanceBefore(transaction.getBalanceBefore());
        dto.setBalanceAfter(transaction.getBalanceAfter());

        if (transaction.getOrder() != null) {
            dto.setOrderId(transaction.getOrder().getId());
            dto.setOrderNumber(transaction.getOrder().getOrderNumber());
        }
        if (transaction.getMomoTransaction() != null) {
            dto.setMomoTransactionId(String.valueOf(transaction.getMomoTransaction().getTransId()));
            dto.setMomoOrderId(transaction.getMomoTransaction().getOrderId());
        }

        dto.setDescription(transaction.getDescription());
        dto.setCreatedAt(transaction.getCreatedAt());
        return dto;
    }
}
