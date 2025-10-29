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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZaloPayPaymentService {

    @Value("${zalopay.endpoint}")
    private String endpoint;

    @Value("${zalopay.appId}")
    private String appId;

    @Value("${zalopay.key1}")
    private String key1;

    @Value("${zalopay.key2}")
    private String key2;

    @Value("${zalopay.callbackUrl}")
    private String callbackUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String createPaymentUrl(BigDecimal amount, String orderId, String description) {
        try {
            long appTime = System.currentTimeMillis();
            SimpleDateFormat formatter = new SimpleDateFormat("yyMMdd");
            String appTransId = formatter.format(new Date()) + "_" + appTime;

            Map<String, Object> embedData = new HashMap<>();
            Map<String, Object> item = new HashMap<>();

            Map<String, Object> order = new HashMap<>();
            order.put("app_id", Integer.parseInt(appId));
            order.put("app_user", "user_" + orderId);
            order.put("app_time", appTime);
            order.put("app_trans_id", appTransId);
            // ZaloPay expects integer amount (VND). Round to 0 decimals
            String amountStr = amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
            order.put("amount", amountStr);
            order.put("item", "[]");
            order.put("description", description);
            order.put("embed_data", objectMapper.writeValueAsString(embedData));
            order.put("bank_code", "");
            order.put("callback_url", callbackUrl);

            // Build MAC theo đúng format ZaloPay
            String data = appId + "|" + appTransId + "|" + "user_" + orderId + "|"
                    + amountStr + "|" + appTime + "|" + "{}" + "|" + "[]";
            String mac = hmacSHA256(key1, data);
            order.put("mac", mac);

            log.info("ZaloPay payment request for order: {}", orderId);
            log.debug("ZaloPay MAC data: {}", data);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded");

            // ZaloPay yêu cầu form-urlencoded
            StringBuilder formData = new StringBuilder();
            for (Map.Entry<String, Object> entry : order.entrySet()) {
                if (formData.length() > 0) formData.append("&");
                formData.append(entry.getKey()).append("=").append(entry.getValue());
            }

            httpPost.setEntity(new StringEntity(formData.toString(), StandardCharsets.UTF_8));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                log.info("ZaloPay response: {}", responseBody);

                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                Integer returnCode = (Integer) result.get("return_code");
                if (returnCode != null && returnCode == 1) {
                    return (String) result.get("order_url");
                } else {
                    log.error("ZaloPay error: {} - {}", returnCode, result.get("return_message"));
                    throw new RuntimeException("ZaloPay error: " + result.get("return_message"));
                }
            }
        } catch (Exception e) {
            log.error("Failed to create ZaloPay payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create ZaloPay payment: " + e.getMessage());
        }
    }

    public boolean verifyCallback(Map<String, String> params) {
        try {
            String receivedMac = params.get("mac");
            String data = params.get("data");

            String calculatedMac = hmacSHA256(key2, data);

            boolean macValid = calculatedMac.equals(receivedMac);

            // Parse data JSON để lấy status
            Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);
            Integer status = (Integer) dataMap.get("status");

            boolean paymentSuccess = status != null && status == 1;

            log.info("ZaloPay callback verification - MAC valid: {}, Payment success: {}", macValid, paymentSuccess);

            return macValid && paymentSuccess;
        } catch (Exception e) {
            log.error("Failed to verify ZaloPay callback: {}", e.getMessage(), e);
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
