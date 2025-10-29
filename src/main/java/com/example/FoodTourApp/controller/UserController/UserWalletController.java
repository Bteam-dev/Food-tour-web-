package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.WalletDTO.DepositRequestDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletResponseDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletTransactionResponseDTO;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/wallet")
@PreAuthorize("hasAnyRole('USER','SELLER','ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class UserWalletController {

    private final WalletService walletService;

    /**
     * Lấy danh sách phương thức thanh toán khả dụng
     * GET /api/user/wallet/payment-methods
     */
    @GetMapping("/payment-methods")
    public ResponseEntity<?> getPaymentMethods(@AuthenticationPrincipal User user) {
        log.info("User {} is fetching available payment methods", user.getEmail());

        try {
            List<String> paymentMethods = Arrays.asList("paypal", "vnpay", "momo", "zalopay");

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Danh sách phương thức thanh toán");
            result.put("data", paymentMethods);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching payment methods for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Nạp tiền vào ví
     * POST /api/user/wallet/deposit
     */
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@Valid @RequestBody DepositRequestDTO request,
                                     @AuthenticationPrincipal User user) {
        log.info("User {} is depositing {} via {}", user.getEmail(), request.getAmount(), request.getPaymentMethod());

        try {
            List<String> validMethods = Arrays.asList("paypal", "vnpay", "momo", "zalopay");
            if (!validMethods.contains(request.getPaymentMethod().toLowerCase())) {
                throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ");
            }

            WalletResponseDTO response = walletService.initiateDeposit(request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error depositing to wallet for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy thông tin ví
     * GET /api/user/wallet
     */
    @GetMapping
    public ResponseEntity<?> getWalletInfo(@AuthenticationPrincipal User user) {
        log.info("User {} is getting wallet info", user.getEmail());

        try {
            WalletResponseDTO response = walletService.getWalletInfo(user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting wallet info for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lấy lịch sử giao dịch
     * GET /api/user/wallet/transactions
     */
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactionHistory(@AuthenticationPrincipal User user) {
        log.info("User {} is getting transaction history", user.getEmail());

        try {
            List<WalletTransactionResponseDTO> transactions = walletService.getTransactionHistory(user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", transactions);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error getting transaction history for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/paypal/success")
    public ResponseEntity<?> paypalSuccess(@RequestParam Map<String, String> params,
                                           @AuthenticationPrincipal User user) {
        try {
            walletService.handlePaymentCallback("paypal", params, user);
            return ResponseEntity.ok("Nạp tiền thành công qua PayPal");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Nạp tiền thất bại: " + e.getMessage());
        }
    }

    @GetMapping("/paypal/cancel")
    public ResponseEntity<?> paypalCancel() {
        return ResponseEntity.ok("Nạp tiền bị hủy qua PayPal");
    }

    @GetMapping("/vnpay/callback")
    public ResponseEntity<?> vnpayCallback(@RequestParam Map<String, String> params,
                                           @AuthenticationPrincipal User user) {
        try {
            walletService.handlePaymentCallback("vnpay", params, user);
            return ResponseEntity.ok("Nạp tiền thành công qua VNPay");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Nạp tiền thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/momo/callback")
    public ResponseEntity<?> momoCallback(@RequestBody Map<String, String> params,
                                          @AuthenticationPrincipal User user) {
        try {
            walletService.handlePaymentCallback("momo", params, user);
            return ResponseEntity.ok("Nạp tiền thành công qua MoMo");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Nạp tiền thất bại: " + e.getMessage());
        }
    }

    @PostMapping("/zalopay/callback")
    public ResponseEntity<?> zalopayCallback(@RequestBody Map<String, String> params,
                                             @AuthenticationPrincipal User user) {
        try {
            walletService.handlePaymentCallback("zalopay", params, user);
            return ResponseEntity.ok("Nạp tiền thành công qua ZaloPay");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Nạp tiền thất bại: " + e.getMessage());
        }
    }
}

