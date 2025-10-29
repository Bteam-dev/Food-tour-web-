package com.example.FoodTourApp.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MoMoPaymentService {

    @Value("${momo.endpoint}")
    private String endpoint;

    @Value("${momo.partnerCode}")
    private String partnerCode;

    @Value("${momo.accessKey}")
    private String accessKey;

    @Value("${momo.secretKey}")
    private String secretKey;

    @Value("${momo.returnUrl}")
    private String returnUrl;

    @Value("${momo.notifyUrl}")
    private String notifyUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String createPaymentUrl(BigDecimal amount, String orderId, String description) {
        try {
            String requestId = UUID.randomUUID().toString();
            // MoMo expects integer amount (VND). Round to 0 decimal places.
            String amountStr = amount.setScale(0, RoundingMode.HALF_UP).toPlainString();

            // Build signature theo đúng format MoMo yêu cầu
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + amountStr +
                    "&extraData=" +
                    "&ipnUrl=" + notifyUrl +
                    "&orderId=" + orderId +
                    "&orderInfo=" + description +
                    "&partnerCode=" + partnerCode +
                    "&redirectUrl=" + returnUrl +
                    "&requestId=" + requestId +
                    "&requestType=captureWallet";

            String signature = hmacSHA256(secretKey, rawSignature);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("partnerCode", partnerCode);
            requestBody.put("accessKey", accessKey);
            requestBody.put("requestId", requestId);
            requestBody.put("amount", amountStr);
            requestBody.put("orderId", orderId);
            requestBody.put("orderInfo", description);
            requestBody.put("redirectUrl", returnUrl);
            requestBody.put("ipnUrl", notifyUrl);
            requestBody.put("extraData", "");
            requestBody.put("requestType", "captureWallet");
            requestBody.put("signature", signature);
            requestBody.put("lang", "vi");

            log.info("MoMo payment request for order: {}", orderId);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.info("MoMo response: {}", responseBody);

                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                if (result.get("payUrl") != null) {
                    return (String) result.get("payUrl");
                } else {
                    log.error("MoMo error: {}", result.get("message"));
                    throw new RuntimeException("MoMo error: " + result.get("message"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to create MoMo payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create MoMo payment: " + e.getMessage());
        }
    }

    public boolean verifyCallback(Map<String, String> params) {
        try {
            String receivedSignature = params.get("signature");

            // Build signature theo đúng format MoMo callback
            String rawSignature = "accessKey=" + accessKey +
                    "&amount=" + params.get("amount") +
                    "&extraData=" + params.getOrDefault("extraData", "") +
                    "&message=" + params.get("message") +
                    "&orderId=" + params.get("orderId") +
                    "&orderInfo=" + params.get("orderInfo") +
                    "&orderType=" + params.get("orderType") +
                    "&partnerCode=" + partnerCode +
                    "&payType=" + params.get("payType") +
                    "&requestId=" + params.get("requestId") +
                    "&responseTime=" + params.get("responseTime") +
                    "&resultCode=" + params.get("resultCode") +
                    "&transId=" + params.get("transId");

            String calculatedSignature = hmacSHA256(secretKey, rawSignature);

            boolean signatureValid = calculatedSignature.equals(receivedSignature);
            boolean paymentSuccess = "0".equals(params.get("resultCode"));

            log.info("MoMo callback verification - Signature valid: {}, Payment success: {}", signatureValid, paymentSuccess);

            return signatureValid && paymentSuccess;
        } catch (Exception e) {
            log.error("Failed to verify MoMo callback: {}", e.getMessage(), e);
            return false;
        }
    }

    private String hmacSHA256(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to calculate HMAC SHA256: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate HMAC SHA256", e);
        }
    }
}
