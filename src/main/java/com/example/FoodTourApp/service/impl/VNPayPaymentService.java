package com.example.FoodTourApp.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayPaymentService {

    @Value("${vnpay.payUrl}")
    private String payUrl;

    @Value("${vnpay.tmnCode}")
    private String tmnCode;

    @Value("${vnpay.hashSecret}")
    private String hashSecret;

    @Value("${vnpay.returnUrl}")
    private String returnUrl;

    public String createPaymentUrl(BigDecimal amount, String orderId, String description) {
        try {
            TreeMap<String, String> params = new TreeMap<>();

            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            String vnp_CreateDate = formatter.format(new Date());

            params.put("vnp_Version", "2.1.0");
            params.put("vnp_Command", "pay");
            params.put("vnp_TmnCode", tmnCode);

            // VNPay expects amount as integer (VND * 100) per original code; use BigDecimal safely
            BigDecimal amountInSmallestUnit = amount.multiply(BigDecimal.valueOf(100));
            params.put("vnp_Amount", String.valueOf(amountInSmallestUnit.longValue()));

            params.put("vnp_CurrCode", "VND");
            params.put("vnp_TxnRef", orderId);
            params.put("vnp_OrderInfo", description);
            params.put("vnp_OrderType", "other");
            params.put("vnp_Locale", "vn");
            params.put("vnp_ReturnUrl", returnUrl);
            params.put("vnp_IpAddr", "127.0.0.1");
            params.put("vnp_CreateDate", vnp_CreateDate);

            String queryString = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            String hashData = params.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));
            String secureHash = hmacSHA512(hashSecret, hashData);

            String paymentUrl = payUrl + "?" + queryString + "&vnp_SecureHash=" + secureHash;
            log.info("VNPay payment URL created for order: {}", orderId);

            return paymentUrl;
        } catch (Exception e) {
            log.error("Failed to create VNPay payment URL: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create VNPay payment URL: " + e.getMessage());
        }
    }

    public boolean verifyCallback(Map<String, String> params) {
        try {
            String vnp_SecureHash = params.remove("vnp_SecureHash");
            String vnp_SecureHashType = params.remove("vnp_SecureHashType");

            TreeMap<String, String> sortedParams = new TreeMap<>(params);

            String hashData = sortedParams.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("&"));

            String calculatedHash = hmacSHA512(hashSecret, hashData);

            boolean hashValid = calculatedHash.equalsIgnoreCase(vnp_SecureHash);
            boolean transactionSuccess = "00".equals(params.get("vnp_ResponseCode"));

            log.info("VNPay callback verification - Hash valid: {}, Transaction success: {}", hashValid, transactionSuccess);

            return hashValid && transactionSuccess;
        } catch (Exception e) {
            log.error("Failed to verify VNPay callback: {}", e.getMessage(), e);
            return false;
        }
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
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
            log.error("Failed to calculate HMAC SHA512: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to calculate HMAC SHA512", e);
        }
    }
}
