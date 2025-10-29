package com.example.FoodTourApp.service.impl;

import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayPalPaymentService {

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    @Value("${paypal.mode}")
    private String mode;

    @Value("${paypal.returnUrl}")
    private String returnUrl;

    @Value("${paypal.cancelUrl}")
    private String cancelUrl;

    public String createPaymentUrl(BigDecimal amount, String currency, String orderId, String description) {
        try {
            APIContext apiContext = new APIContext(clientId, clientSecret, mode);

            Amount paymentAmount = new Amount();

            BigDecimal amountForPayPal;
            String paypalCurrency;

            // If incoming amount is VND, convert to USD using approximate rate 1 USD = 23000 VND
            if (currency != null && currency.equalsIgnoreCase("VND")) {
                BigDecimal rate = BigDecimal.valueOf(23000);
                amountForPayPal = amount.divide(rate, 2, RoundingMode.HALF_UP);
                paypalCurrency = "USD";
            } else {
                // Use provided currency and amount as-is
                amountForPayPal = amount.setScale(2, RoundingMode.HALF_UP);
                paypalCurrency = (currency != null && !currency.isBlank()) ? currency : "USD";
            }

            paymentAmount.setCurrency(paypalCurrency);
            paymentAmount.setTotal(amountForPayPal.toPlainString());

            Transaction transaction = new Transaction();
            transaction.setDescription(description);
            transaction.setAmount(paymentAmount);

            List<Transaction> transactions = new ArrayList<>();
            transactions.add(transaction);

            Payer payer = new Payer();
            payer.setPaymentMethod("paypal");

            Payment payment = new Payment();
            payment.setIntent("sale");
            payment.setPayer(payer);
            payment.setTransactions(transactions);

            RedirectUrls redirectUrls = new RedirectUrls();
            redirectUrls.setReturnUrl(returnUrl + "?orderId=" + orderId);
            redirectUrls.setCancelUrl(cancelUrl);
            payment.setRedirectUrls(redirectUrls);

            Payment createdPayment = payment.create(apiContext);

            log.info("PayPal payment created: {}", createdPayment.getId());

            for (Links link : createdPayment.getLinks()) {
                if (link.getRel().equalsIgnoreCase("approval_url")) {
                    return link.getHref();
                }
            }

            throw new RuntimeException("No approval URL found in PayPal response");

        } catch (PayPalRESTException e) {
            log.error("Failed to create PayPal payment: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create PayPal payment: " + e.getMessage());
        }
    }

    public boolean verifyCallback(Map<String, String> params) {
        try {
            String paymentId = params.get("paymentId");
            String payerId = params.get("PayerID");

            if (paymentId == null || payerId == null) {
                log.error("Missing paymentId or PayerID in callback");
                return false;
            }

            APIContext apiContext = new APIContext(clientId, clientSecret, mode);

            Payment payment = Payment.get(apiContext, paymentId);

            if ("approved".equals(payment.getState())) {
                PaymentExecution execution = new PaymentExecution();
                execution.setPayerId(payerId);
                Payment executedPayment = payment.execute(apiContext, execution);

                log.info("PayPal payment executed successfully: {}", executedPayment.getId());
                return "approved".equals(executedPayment.getState());
            }

            log.warn("PayPal payment not approved: {}", payment.getState());
            return false;

        } catch (PayPalRESTException e) {
            log.error("Failed to verify PayPal callback: {}", e.getMessage(), e);
            return false;
        }
    }
}
