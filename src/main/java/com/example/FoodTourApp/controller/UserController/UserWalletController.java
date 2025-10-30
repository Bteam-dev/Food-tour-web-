package com.example.FoodTourApp.controller.UserController;

import com.example.FoodTourApp.DTO.WalletDTO.*;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.MomoPaymentService;
import com.example.FoodTourApp.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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
    private final MomoPaymentService momoPaymentService;

    /**
     * Nạp tiền vào ví qua MOMO (Khuyên dùng)
     * POST /api/user/wallet/deposit/momo
     */
    @PostMapping("/deposit/momo")
    public ResponseEntity<?> depositViaMomo(@Valid @RequestBody MomoDepositRequestDTO request,
                                            @AuthenticationPrincipal User user) {
        log.info("User {} is creating MOMO deposit payment for amount {}", user.getEmail(), request.getAmount());

        try {
            MomoPaymentResponseDTO response = momoPaymentService.createDepositPayment(request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Tạo link thanh toán MOMO thành công");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error creating MOMO deposit for user {}: {}", user.getEmail(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Nạp tiền vào ví (Direct - chỉ dùng cho testing)
     * POST /api/user/wallet/deposit
     */
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@Valid @RequestBody DepositRequestDTO request,
                                     @AuthenticationPrincipal User user) {
        log.info("User {} is depositing {} to wallet", user.getEmail(), request.getAmount());

        try {
            WalletResponseDTO response = walletService.deposit(request, user);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Nạp tiền thành công");
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
}
