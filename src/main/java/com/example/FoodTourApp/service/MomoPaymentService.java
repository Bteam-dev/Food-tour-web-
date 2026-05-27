package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.WalletDTO.MomoCallbackDTO;
import com.example.FoodTourApp.DTO.WalletDTO.MomoDepositRequestDTO;
import com.example.FoodTourApp.DTO.WalletDTO.MomoPaymentResponseDTO;
import com.example.FoodTourApp.entity.User;

public interface MomoPaymentService {

    /**
     * Tạo link thanh toán MOMO để nạp tiền vào ví
     */
    MomoPaymentResponseDTO createDepositPayment(MomoDepositRequestDTO request, User user);

    /**
     * Xử lý callback từ MOMO sau khi user thanh toán
     */
    void handleCallback(MomoCallbackDTO callback);

    /**
     * Xác thực chữ ký từ MOMO
     */
    boolean verifySignature(MomoCallbackDTO callback);
}

