package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.WalletDTO.MomoCallbackDTO;
import com.example.FoodTourApp.service.MomoPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/momo")
@RequiredArgsConstructor
@Slf4j
public class MomoCallbackController {

    private final MomoPaymentService momoPaymentService;

    /**
     * Callback endpoint cho MOMO - khi user hoàn tất thanh toán
     * GET /api/public/momo/callback
     */
    @GetMapping("/callback")
    public ResponseEntity<?> handleCallback(@ModelAttribute MomoCallbackDTO callback) {
        log.info("Received MOMO callback for orderId: {}", callback.getOrderId());

        try {
            // Xác thực signature (optional nhưng nên có cho production)
            // if (!momoPaymentService.verifySignature(callback)) {
            //     log.warn("Invalid signature from MOMO callback");
            //     return ResponseEntity.badRequest().body("Invalid signature");
            // }

            momoPaymentService.handleCallback(callback);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", callback.getResultCode() == 0 ?
                "Nạp tiền thành công!" : "Thanh toán thất bại: " + callback.getMessage());
            result.put("resultCode", callback.getResultCode());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error handling MOMO callback: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * IPN (Instant Payment Notification) endpoint cho MOMO
     * POST /api/public/momo/ipn
     */
    @PostMapping("/ipn")
    public ResponseEntity<?> handleIPN(@RequestBody MomoCallbackDTO callback) {
        log.info("Received MOMO IPN for orderId: {}", callback.getOrderId());

        try {
            // Xác thực signature
            if (!momoPaymentService.verifySignature(callback)) {
                log.warn("Invalid signature from MOMO IPN");
                Map<String, Object> error = new HashMap<>();
                error.put("message", "Invalid signature");
                error.put("resultCode", 97);
                return ResponseEntity.ok(error);
            }

            momoPaymentService.handleCallback(callback);

            // Trả về response cho MOMO theo format của họ
            Map<String, Object> result = new HashMap<>();
            result.put("message", "success");
            result.put("resultCode", 0);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error handling MOMO IPN: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("message", e.getMessage());
            error.put("resultCode", 99);
            return ResponseEntity.ok(error);
        }
    }
}

