package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.WalletDTO.MomoCallbackDTO;
import com.example.FoodTourApp.DTO.WalletDTO.MomoDepositRequestDTO;
import com.example.FoodTourApp.DTO.WalletDTO.MomoPaymentResponseDTO;
import com.example.FoodTourApp.entity.MomoTransaction;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.entity.WalletTransaction;
import com.example.FoodTourApp.repository.MomoTransactionRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.repository.WalletTransactionRepository;
import com.example.FoodTourApp.service.MomoPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MomoPaymentServiceImpl implements MomoPaymentService {

    private final MomoTransactionRepository momoTransactionRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${momo.partner-code:MOMO}")
    private String partnerCode;

    @Value("${momo.access-key:F8BBA842ECF85}")
    private String accessKey;

    @Value("${momo.secret-key:K951B6PE1waDMi640xX08PD3vg6EkVlz}")
    private String secretKey;

    @Value("${momo.endpoint:https://test-payment.momo.vn/v2/gateway/api/create}")
    private String momoEndpoint;

    @Value("${momo.return-url:http://localhost:8080/api/public/momo/callback}")
    private String defaultReturnUrl;

    @Value("${momo.notify-url:http://localhost:8080/api/public/momo/ipn}")
    private String notifyUrl;

    @Override
    public MomoPaymentResponseDTO createDepositPayment(MomoDepositRequestDTO request, User user) {
        log.info("Creating MOMO deposit payment for user {}, amount: {}, paymentMethod: {}",
            user.getId(), request.getAmount(), request.getPaymentMethod());

        try {
            // Tạo orderId và requestId unique
            String orderId = "DEPOSIT_" + user.getId() + "_" + System.currentTimeMillis();
            String requestId = UUID.randomUUID().toString();
            Long amount = request.getAmount().setScale(0, BigDecimal.ROUND_DOWN).longValue();
            String orderInfo = request.getDescription() != null ?
                request.getDescription() : "Nạp tiền vào ví FoodTour";
            String returnUrl = request.getReturnUrl() != null ?
                request.getReturnUrl() : defaultReturnUrl;

            // Xác định requestType dựa trên paymentMethod
            // "app" hoặc null => payWithATM (cho phép thanh toán qua App MOMO)
            // "card" => captureWallet (thanh toán qua thẻ test)
            String requestType = "app".equals(request.getPaymentMethod()) || request.getPaymentMethod() == null
                ? "payWithATM"
                : "captureWallet";

            log.info("Using requestType: {} for paymentMethod: {}", requestType, request.getPaymentMethod());

            // Tạo raw signature
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + amount +
                    "&extraData=" +
                    "&ipnUrl=" + notifyUrl +
                    "&orderId=" + orderId +
                    "&orderInfo=" + orderInfo +
                    "&partnerCode=" + partnerCode +
                    "&redirectUrl=" + returnUrl +
                    "&requestId=" + requestId +
                    "&requestType=" + requestType;

            log.info("Raw signature: {}", rawSignature);

            // Tạo HMAC SHA256 signature
            String signature = hmacSHA256(rawSignature, secretKey);
            log.info("Signature: {}", signature);

            // Tạo request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("partnerCode", partnerCode);
            requestBody.put("accessKey", accessKey);
            requestBody.put("requestId", requestId);
            requestBody.put("amount", amount);
            requestBody.put("orderId", orderId);
            requestBody.put("orderInfo", orderInfo);
            requestBody.put("redirectUrl", returnUrl);
            requestBody.put("ipnUrl", notifyUrl);
            requestBody.put("requestType", requestType);
            requestBody.put("extraData", "");
            requestBody.put("lang", "vi");
            requestBody.put("signature", signature);

            // Lưu transaction vào DB trước khi gọi MOMO
            MomoTransaction momoTransaction = new MomoTransaction();
            momoTransaction.setUser(user);
            momoTransaction.setOrderId(orderId);
            momoTransaction.setRequestId(requestId);
            momoTransaction.setAmount(request.getAmount().setScale(2, BigDecimal.ROUND_HALF_UP));
            momoTransaction.setStatus(MomoTransaction.MomoTransactionStatus.pending);
            momoTransaction.setDescription(orderInfo);
            momoTransaction.setCreatedAt(LocalDateTime.now());
            momoTransactionRepository.save(momoTransaction);

            // Gọi API MOMO
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Calling MOMO API: {}", momoEndpoint);
            log.info("Request body: {}", objectMapper.writeValueAsString(requestBody));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    momoEndpoint,
                    entity,
                    Map.class
            );

            log.info("MOMO response: {}", objectMapper.writeValueAsString(response));

            // Parse response
            if (response == null) {
                throw new RuntimeException("MOMO API returned null response");
            }

            Integer resultCode = (Integer) response.get("resultCode");
            String message = (String) response.get("message");
            String payUrl = (String) response.get("payUrl");

            // Cập nhật transaction
            momoTransaction.setResultCode(resultCode);
            momoTransaction.setMessage(message);
            momoTransaction.setPayUrl(payUrl);
            momoTransaction.setUpdatedAt(LocalDateTime.now());
            momoTransactionRepository.save(momoTransaction);

            // Tạo response
            MomoPaymentResponseDTO paymentResponse = new MomoPaymentResponseDTO();
            paymentResponse.setPayUrl(payUrl);
            paymentResponse.setOrderId(orderId);
            paymentResponse.setRequestId(requestId);
            paymentResponse.setAmount(amount);
            paymentResponse.setMessage(message);
            paymentResponse.setResultCode(resultCode);

            return paymentResponse;

        } catch (Exception e) {
            log.error("Error creating MOMO payment: {}", e.getMessage(), e);
            throw new RuntimeException("Không thể tạo link thanh toán MOMO: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void handleCallback(MomoCallbackDTO callback) {
        log.info("Handling MOMO callback for orderId: {}", callback.getOrderId());
        log.info("Result code: {}, Message: {}", callback.getResultCode(), callback.getMessage());

        try {
            // Tìm transaction
            MomoTransaction momoTransaction = momoTransactionRepository.findByOrderId(callback.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Transaction not found: " + callback.getOrderId()));

            // Kiểm tra transaction đã được xử lý chưa
            if (momoTransaction.getStatus() != MomoTransaction.MomoTransactionStatus.pending) {
                log.warn("Transaction {} already processed with status: {}",
                    callback.getOrderId(), momoTransaction.getStatus());
                return;
            }

            // Cập nhật thông tin từ callback
            momoTransaction.setTransId(callback.getTransId());
            momoTransaction.setResultCode(callback.getResultCode());
            momoTransaction.setMessage(callback.getMessage());
            momoTransaction.setUpdatedAt(LocalDateTime.now());

            // Xử lý theo result code
            if (callback.getResultCode() == 0) {
                // Thanh toán thành công
                momoTransaction.setStatus(MomoTransaction.MomoTransactionStatus.success);
                momoTransaction.setCompletedAt(LocalDateTime.now());

                // Cộng tiền vào ví user
                User user = momoTransaction.getUser();
                User dbUser = userRepository.findById(user.getId())
                        .orElseThrow(() -> new RuntimeException("User not found"));

                BigDecimal balanceBefore = dbUser.getWalletBalance();
                BigDecimal balanceAfter = balanceBefore.add(momoTransaction.getAmount());

                dbUser.setWalletBalance(balanceAfter);
                dbUser.setUpdatedAt(LocalDateTime.now());
                userRepository.save(dbUser);

                // Tạo wallet transaction record
                WalletTransaction walletTransaction = new WalletTransaction();
                walletTransaction.setUser(dbUser);
                walletTransaction.setTransactionType(WalletTransaction.TransactionType.deposit);
                walletTransaction.setAmount(momoTransaction.getAmount());
                walletTransaction.setBalanceBefore(balanceBefore);
                walletTransaction.setBalanceAfter(balanceAfter);
                walletTransaction.setDescription("Nạp tiền qua MOMO - " + momoTransaction.getDescription() +
                    " (TransID: " + callback.getTransId() + ")");
                walletTransaction.setCreatedAt(LocalDateTime.now());
                walletTransactionRepository.save(walletTransaction);

                log.info("Deposit successful for user {}. Balance: {} -> {}",
                    user.getId(), balanceBefore, balanceAfter);

            } else {
                // Thanh toán thất bại
                momoTransaction.setStatus(MomoTransaction.MomoTransactionStatus.failed);
                log.warn("MOMO payment failed for order {}: {}",
                    callback.getOrderId(), callback.getMessage());
            }

            momoTransactionRepository.save(momoTransaction);

        } catch (Exception e) {
            log.error("Error handling MOMO callback: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing MOMO callback: " + e.getMessage());
        }
    }

    @Override
    public boolean verifySignature(MomoCallbackDTO callback) {
        try {
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + callback.getAmount() +
                    "&extraData=" + (callback.getExtraData() != null ? callback.getExtraData() : "") +
                    "&message=" + callback.getMessage() +
                    "&orderId=" + callback.getOrderId() +
                    "&orderInfo=" + callback.getOrderInfo() +
                    "&orderType=" + callback.getOrderType() +
                    "&partnerCode=" + callback.getPartnerCode() +
                    "&payType=" + callback.getPayType() +
                    "&requestId=" + callback.getRequestId() +
                    "&responseTime=" + callback.getResponseTime() +
                    "&resultCode=" + callback.getResultCode() +
                    "&transId=" + callback.getTransId();

            String signature = hmacSHA256(rawSignature, secretKey);

            boolean isValid = signature.equals(callback.getSignature());
            log.info("Signature verification: {}", isValid ? "VALID" : "INVALID");

            return isValid;

        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage(), e);
            return false;
        }
    }

    private String hmacSHA256(String data, String key) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();

        } catch (Exception e) {
            log.error("Error creating HMAC SHA256: {}", e.getMessage(), e);
            throw new RuntimeException("Error creating signature", e);
        }
    }
}
