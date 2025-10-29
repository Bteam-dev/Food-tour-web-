package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.WalletDTO.DepositRequestDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletResponseDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletTransactionResponseDTO;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.WalletTransaction;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.repository.WalletTransactionRepository;
import com.example.FoodTourApp.service.WalletService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PayPalPaymentService payPalService;
    private final VNPayPaymentService vnPayService;
    private final MoMoPaymentService moMoService;
    private final ZaloPayPaymentService zaloPayService;


    @Override
    @Transactional
    public WalletResponseDTO initiateDeposit(DepositRequestDTO request, User user) {
        log.info("User {} initiating deposit of {} via {}", user.getId(), request.getAmount(), request.getPaymentMethod());

        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Tạo transaction pending
        WalletTransaction pendingTransaction = new WalletTransaction();
        pendingTransaction.setUser(dbUser);
        pendingTransaction.setTransactionType(WalletTransaction.TransactionType.deposit);
        pendingTransaction.setAmount(request.getAmount());
        pendingTransaction.setBalanceBefore(dbUser.getWalletBalance());
        // Use BigDecimal add instead of primitive +
        pendingTransaction.setBalanceAfter(dbUser.getWalletBalance().add(request.getAmount()));
        pendingTransaction.setDescription(request.getDescription() != null ? request.getDescription() : "Nạp tiền qua " + request.getPaymentMethod());
        pendingTransaction.setCreatedAt(LocalDateTime.now());
        pendingTransaction.setStatus(WalletTransaction.TransactionStatus.PENDING);
        String externalOrderId = UUID.randomUUID().toString();
        pendingTransaction.setExternalOrderId(externalOrderId);
        walletTransactionRepository.save(pendingTransaction);

        String paymentUrl;
        try {
            paymentUrl = switch (request.getPaymentMethod().toLowerCase()) {
                case "paypal" -> payPalService.createPaymentUrl(request.getAmount(), "VND", externalOrderId, "Deposit to wallet");
                case "vnpay" -> vnPayService.createPaymentUrl(request.getAmount(), externalOrderId, "Deposit to wallet");
                case "momo" -> moMoService.createPaymentUrl(request.getAmount(), externalOrderId, "Deposit to wallet");
                case "zalopay" -> zaloPayService.createPaymentUrl(request.getAmount(), externalOrderId, "Deposit to wallet");
                default -> throw new RuntimeException("Unsupported payment method: " + request.getPaymentMethod());
            };
        } catch (Exception e) {
            pendingTransaction.setStatus(WalletTransaction.TransactionStatus.FAILED);
            walletTransactionRepository.save(pendingTransaction);
            throw new RuntimeException("Failed to generate payment URL: " + e.getMessage());
        }

        log.info("Generated payment URL for user {}: {}", user.getId(), paymentUrl);

        // Trả về DTO có paymentUrl
        WalletResponseDTO response = new WalletResponseDTO();
        response.setUserId(dbUser.getId());
        response.setFullName(dbUser.getFullName());
        response.setBalance(dbUser.getWalletBalance());
        response.setPaymentUrl(paymentUrl);
        response.setMessage("Yêu cầu nạp tiền đã được khởi tạo. Vui lòng thanh toán tại liên kết bên dưới.");
        return response;
    }

    @Transactional
    public void handlePaymentCallback(String paymentMethod, Map<String, String> params, User user) {
        log.info("Handling callback for {} from user {}", paymentMethod, user.getId());

        boolean isSuccess;
        BigDecimal amount = BigDecimal.ZERO;
        String externalOrderId = params.get("orderId"); // Mặc định, sẽ override cho từng cổng

        switch (paymentMethod.toLowerCase()) {
            case "paypal":
                isSuccess = payPalService.verifyCallback(params);
                try {
                    String amt = params.get("amount");
                    amount = (amt != null && !amt.isBlank()) ? new BigDecimal(amt) : BigDecimal.ZERO;
                } catch (Exception ex) {
                    log.warn("Unable to parse PayPal amount from callback: {}", params.get("amount"));
                    amount = BigDecimal.ZERO;
                }
                externalOrderId = params.get("orderId");
                break;
            case "vnpay":
                isSuccess = vnPayService.verifyCallback(params);
                try {
                    String vnpAmt = params.get("vnp_Amount");
                    if (vnpAmt != null && !vnpAmt.isBlank()) {
                        BigDecimal vnpAmount = new BigDecimal(vnpAmt);
                        // VNPay amount is sent as VND*100
                        amount = vnpAmount.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    } else {
                        amount = BigDecimal.ZERO;
                    }
                } catch (Exception ex) {
                    log.warn("Unable to parse VNPay amount from callback: {}", params.get("vnp_Amount"));
                    amount = BigDecimal.ZERO;
                }
                externalOrderId = params.get("vnp_TxnRef");
                break;
            case "momo":
                isSuccess = moMoService.verifyCallback(params);
                try {
                    String amt = params.get("amount");
                    amount = (amt != null && !amt.isBlank()) ? new BigDecimal(amt) : BigDecimal.ZERO;
                } catch (Exception ex) {
                    log.warn("Unable to parse MoMo amount from callback: {}", params.get("amount"));
                    amount = BigDecimal.ZERO;
                }
                externalOrderId = params.get("requestId");
                break;
            case "zalopay":
                isSuccess = zaloPayService.verifyCallback(params);
                try {
                    String amt = params.get("amount");
                    amount = (amt != null && !amt.isBlank()) ? new BigDecimal(amt) : BigDecimal.ZERO;
                } catch (Exception ex) {
                    log.warn("Unable to parse ZaloPay amount from callback: {}", params.get("amount"));
                    amount = BigDecimal.ZERO;
                }
                externalOrderId = params.get("apptransid");
                break;
            default:
                throw new RuntimeException("Unsupported payment method");
        }

        WalletTransaction transaction = walletTransactionRepository.findByExternalOrderId(externalOrderId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!isSuccess) {
            transaction.setStatus(WalletTransaction.TransactionStatus.FAILED);
            walletTransactionRepository.save(transaction);
            log.warn("Payment failed for user {} via {}", user.getId(), paymentMethod);
            return;
        }

        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal balanceBefore = dbUser.getWalletBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        dbUser.setWalletBalance(balanceAfter);
        dbUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbUser);

        transaction.setBalanceAfter(balanceAfter);
        transaction.setStatus(WalletTransaction.TransactionStatus.SUCCESS);
        walletTransactionRepository.save(transaction);

        log.info("Deposit successful via {}. User {} balance: {} -> {}", paymentMethod, user.getId(), balanceBefore, balanceAfter);
    }

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
    public List<WalletTransactionResponseDTO> getTransactionHistory(User user) {
        List<WalletTransaction> transactions = walletTransactionRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId());

        return transactions.stream()
                .map(this::convertToTransactionDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void processOrderPayment(Order order) {
        log.info("Processing wallet payment for order {}", order.getId());

        // 1. Trừ tiền người mua
        User buyer = order.getUser();
        User dbBuyer = userRepository.findById(buyer.getId())
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        BigDecimal buyerBalanceBefore = dbBuyer.getWalletBalance();

        BigDecimal orderTotal = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;

        if (buyerBalanceBefore.compareTo(orderTotal) < 0) {
            throw new RuntimeException("Số dư ví không đủ để thanh toán đơn hàng này. Cần: "
                    + orderTotal + " VND, Có: " + buyerBalanceBefore + " VND");
        }

        BigDecimal buyerBalanceAfter = buyerBalanceBefore.subtract(orderTotal);
        dbBuyer.setWalletBalance(buyerBalanceAfter);
        dbBuyer.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbBuyer);

        // Tạo transaction record cho người mua
        WalletTransaction buyerTransaction = new WalletTransaction();
        buyerTransaction.setUser(dbBuyer);
        buyerTransaction.setTransactionType(WalletTransaction.TransactionType.payment);
        buyerTransaction.setAmount(orderTotal);
        buyerTransaction.setBalanceBefore(buyerBalanceBefore);
        buyerTransaction.setBalanceAfter(buyerBalanceAfter);
        buyerTransaction.setOrder(order);
        buyerTransaction.setDescription("Thanh toán đơn hàng #" + order.getOrderNumber());
        buyerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(buyerTransaction);

        log.info("Deducted {} from buyer {}. Balance: {} -> {}",
                orderTotal, dbBuyer.getId(), buyerBalanceBefore, buyerBalanceAfter);

        // 2. Cộng tiền người bán
        User seller = order.getShop().getSeller();
        User dbSeller = userRepository.findById(seller.getId())
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        BigDecimal sellerBalanceBefore = dbSeller.getWalletBalance();
        BigDecimal sellerBalanceAfter = sellerBalanceBefore.add(orderTotal);
        dbSeller.setWalletBalance(sellerBalanceAfter);
        dbSeller.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbSeller);

        // Tạo transaction record cho người bán
        WalletTransaction sellerTransaction = new WalletTransaction();
        sellerTransaction.setUser(dbSeller);
        sellerTransaction.setTransactionType(WalletTransaction.TransactionType.received_payment);
        sellerTransaction.setAmount(orderTotal);
        sellerTransaction.setBalanceBefore(sellerBalanceBefore);
        sellerTransaction.setBalanceAfter(sellerBalanceAfter);
        sellerTransaction.setOrder(order);
        sellerTransaction.setDescription("Nhận tiền từ đơn hàng #" + order.getOrderNumber());
        sellerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(sellerTransaction);

        log.info("Added {} to seller {}. Balance: {} -> {}",
                orderTotal, dbSeller.getId(), sellerBalanceBefore, sellerBalanceAfter);

        // 3. Cập nhật trạng thái đơn hàng
        order.setPaymentStatus(Order.PaymentStatus.paid);
        order.setUpdatedAt(LocalDateTime.now());

        log.info("Order {} payment completed successfully via wallet", order.getId());
    }

    @Override
    @Transactional
    public void refundOrder(Order order) {
        log.info("Processing refund for order {}", order.getId());

        // Chỉ hoàn tiền nếu đơn hàng đã thanh toán bằng ví
        if (order.getPaymentMethod() != Order.PaymentMethod.app_wallet) {
            log.warn("Order {} was not paid by wallet, skip refund", order.getId());
            return;
        }

        if (order.getPaymentStatus() != Order.PaymentStatus.paid) {
            log.warn("Order {} was not paid, skip refund", order.getId());
            return;
        }

        // 1. Hoàn tiền cho người mua
        User buyer = order.getUser();
        User dbBuyer = userRepository.findById(buyer.getId())
                .orElseThrow(() -> new RuntimeException("Buyer not found"));

        BigDecimal buyerBalanceBefore = dbBuyer.getWalletBalance();
        BigDecimal refundAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal buyerBalanceAfter = buyerBalanceBefore.add(refundAmount);
        dbBuyer.setWalletBalance(buyerBalanceAfter);
        dbBuyer.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbBuyer);

        // Tạo transaction record cho người mua
        WalletTransaction buyerTransaction = new WalletTransaction();
        buyerTransaction.setUser(dbBuyer);
        buyerTransaction.setTransactionType(WalletTransaction.TransactionType.refund);
        buyerTransaction.setAmount(refundAmount);
        buyerTransaction.setBalanceBefore(buyerBalanceBefore);
        buyerTransaction.setBalanceAfter(buyerBalanceAfter);
        buyerTransaction.setOrder(order);
        buyerTransaction.setDescription("Hoàn tiền đơn hàng #" + order.getOrderNumber());
        buyerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(buyerTransaction);

        log.info("Refunded {} to buyer {}. Balance: {} -> {}",
                refundAmount, dbBuyer.getId(), buyerBalanceBefore, buyerBalanceAfter);

        // 2. Trừ tiền người bán
        User seller = order.getShop().getSeller();
        User dbSeller = userRepository.findById(seller.getId())
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        BigDecimal sellerBalanceBefore = dbSeller.getWalletBalance();
        BigDecimal sellerBalanceAfter = sellerBalanceBefore.subtract(refundAmount);

        if (sellerBalanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Seller {} has insufficient balance for refund. Balance will be negative.", dbSeller.getId());
        }

        dbSeller.setWalletBalance(sellerBalanceAfter);
        dbSeller.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbSeller);

        // Tạo transaction record cho người bán
        WalletTransaction sellerTransaction = new WalletTransaction();
        sellerTransaction.setUser(dbSeller);
        sellerTransaction.setTransactionType(WalletTransaction.TransactionType.refund);
        // Use negate instead of unary -
        sellerTransaction.setAmount(refundAmount.negate()); // Số âm để biểu thị trừ tiền
        sellerTransaction.setBalanceBefore(sellerBalanceBefore);
        sellerTransaction.setBalanceAfter(sellerBalanceAfter);
        sellerTransaction.setOrder(order);
        sellerTransaction.setDescription("Hoàn tiền đơn hàng #" + order.getOrderNumber());
        sellerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(sellerTransaction);

        log.info("Deducted {} from seller {}. Balance: {} -> {}",
                refundAmount, dbSeller.getId(), sellerBalanceBefore, sellerBalanceAfter);

        // 3. Cập nhật trạng thái đơn hàng
        order.setPaymentStatus(Order.PaymentStatus.refunded);
        order.setUpdatedAt(LocalDateTime.now());

        log.info("Order {} refund completed successfully", order.getId());
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

        dto.setDescription(transaction.getDescription());
        dto.setCreatedAt(transaction.getCreatedAt());

        return dto;
    }
}

