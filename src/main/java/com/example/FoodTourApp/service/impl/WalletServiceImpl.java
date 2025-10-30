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
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {

    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Override
    @Transactional
    public WalletResponseDTO deposit(DepositRequestDTO request, User user) {
        log.info("User {} is depositing {} to wallet", user.getId(), request.getAmount());

        // Lấy user mới nhất từ DB
        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        BigDecimal balanceBefore = dbUser.getWalletBalance();
        BigDecimal balanceAfter = balanceBefore.add(request.getAmount());

        // Cập nhật số dư
        dbUser.setWalletBalance(balanceAfter);
        dbUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbUser);

        // Tạo transaction record
        WalletTransaction transaction = new WalletTransaction();
        transaction.setUser(dbUser);
        transaction.setTransactionType(WalletTransaction.TransactionType.deposit);
        transaction.setAmount(request.getAmount());
        transaction.setBalanceBefore(balanceBefore);
        transaction.setBalanceAfter(balanceAfter);
        transaction.setDescription(request.getDescription() != null ? request.getDescription() : "Nạp tiền vào ví");
        transaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(transaction);

        log.info("Deposit successful. User {} balance: {} -> {}", user.getId(), balanceBefore, balanceAfter);

        return getWalletInfo(dbUser);
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

        if (buyerBalanceBefore.compareTo(order.getTotalAmount()) < 0) {
            throw new RuntimeException("Số dư ví không đủ để thanh toán đơn hàng này. Cần: "
                    + order.getTotalAmount() + " VND, Có: " + buyerBalanceBefore + " VND");
        }

        BigDecimal buyerBalanceAfter = buyerBalanceBefore.subtract(order.getTotalAmount());
        dbBuyer.setWalletBalance(buyerBalanceAfter);
        dbBuyer.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbBuyer);

        // Tạo transaction record cho người mua
        WalletTransaction buyerTransaction = new WalletTransaction();
        buyerTransaction.setUser(dbBuyer);
        buyerTransaction.setTransactionType(WalletTransaction.TransactionType.payment);
        buyerTransaction.setAmount(order.getTotalAmount());
        buyerTransaction.setBalanceBefore(buyerBalanceBefore);
        buyerTransaction.setBalanceAfter(buyerBalanceAfter);
        buyerTransaction.setOrder(order);
        buyerTransaction.setDescription("Thanh toán đơn hàng #" + order.getOrderNumber());
        buyerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(buyerTransaction);

        log.info("Deducted {} from buyer {}. Balance: {} -> {}",
                order.getTotalAmount(), dbBuyer.getId(), buyerBalanceBefore, buyerBalanceAfter);

        // 2. Cộng tiền người bán
        User seller = order.getShop().getSeller();
        User dbSeller = userRepository.findById(seller.getId())
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        BigDecimal sellerBalanceBefore = dbSeller.getWalletBalance();
        BigDecimal sellerBalanceAfter = sellerBalanceBefore.add(order.getTotalAmount());
        dbSeller.setWalletBalance(sellerBalanceAfter);
        dbSeller.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbSeller);

        // Tạo transaction record cho người bán
        WalletTransaction sellerTransaction = new WalletTransaction();
        sellerTransaction.setUser(dbSeller);
        sellerTransaction.setTransactionType(WalletTransaction.TransactionType.received_payment);
        sellerTransaction.setAmount(order.getTotalAmount());
        sellerTransaction.setBalanceBefore(sellerBalanceBefore);
        sellerTransaction.setBalanceAfter(sellerBalanceAfter);
        sellerTransaction.setOrder(order);
        sellerTransaction.setDescription("Nhận tiền từ đơn hàng #" + order.getOrderNumber());
        sellerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(sellerTransaction);

        log.info("Added {} to seller {}. Balance: {} -> {}",
                order.getTotalAmount(), dbSeller.getId(), sellerBalanceBefore, sellerBalanceAfter);

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
        BigDecimal buyerBalanceAfter = buyerBalanceBefore.add(order.getTotalAmount());
        dbBuyer.setWalletBalance(buyerBalanceAfter);
        dbBuyer.setUpdatedAt(LocalDateTime.now());
        userRepository.save(dbBuyer);

        // Tạo transaction record cho người mua
        WalletTransaction buyerTransaction = new WalletTransaction();
        buyerTransaction.setUser(dbBuyer);
        buyerTransaction.setTransactionType(WalletTransaction.TransactionType.refund);
        buyerTransaction.setAmount(order.getTotalAmount());
        buyerTransaction.setBalanceBefore(buyerBalanceBefore);
        buyerTransaction.setBalanceAfter(buyerBalanceAfter);
        buyerTransaction.setOrder(order);
        buyerTransaction.setDescription("Hoàn tiền đơn hàng #" + order.getOrderNumber());
        buyerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(buyerTransaction);

        log.info("Refunded {} to buyer {}. Balance: {} -> {}",
                order.getTotalAmount(), dbBuyer.getId(), buyerBalanceBefore, buyerBalanceAfter);

        // 2. Trừ tiền người bán
        User seller = order.getShop().getSeller();
        User dbSeller = userRepository.findById(seller.getId())
                .orElseThrow(() -> new RuntimeException("Seller not found"));

        BigDecimal sellerBalanceBefore = dbSeller.getWalletBalance();
        BigDecimal sellerBalanceAfter = sellerBalanceBefore.subtract(order.getTotalAmount());

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
        sellerTransaction.setAmount(order.getTotalAmount().negate()); // Số âm để biểu thị trừ tiền
        sellerTransaction.setBalanceBefore(sellerBalanceBefore);
        sellerTransaction.setBalanceAfter(sellerBalanceAfter);
        sellerTransaction.setOrder(order);
        sellerTransaction.setDescription("Hoàn tiền đơn hàng #" + order.getOrderNumber());
        sellerTransaction.setCreatedAt(LocalDateTime.now());
        walletTransactionRepository.save(sellerTransaction);

        log.info("Deducted {} from seller {}. Balance: {} -> {}",
                order.getTotalAmount(), dbSeller.getId(), sellerBalanceBefore, sellerBalanceAfter);

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

        // Nếu là giao dịch liên quan đến Order (payment, refund)
        if (transaction.getOrder() != null) {
            dto.setOrderId(transaction.getOrder().getId());
            dto.setOrderNumber(transaction.getOrder().getOrderNumber());
        }

        // Nếu là giao dịch deposit qua MOMO
        if (transaction.getMomoTransaction() != null) {
            dto.setMomoTransactionId(String.valueOf(transaction.getMomoTransaction().getTransId()));
            dto.setMomoOrderId(transaction.getMomoTransaction().getOrderId());
        }

        dto.setDescription(transaction.getDescription());
        dto.setCreatedAt(transaction.getCreatedAt());

        return dto;
    }
}
