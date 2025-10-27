package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.WalletDTO.DepositRequestDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletResponseDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletTransactionResponseDTO;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;

import java.util.List;

public interface WalletService {

    /**
     * Nạp tiền vào ví
     */
    WalletResponseDTO deposit(DepositRequestDTO request, User user);

    /**
     * Lấy thông tin ví của user
     */
    WalletResponseDTO getWalletInfo(User user);

    /**
     * Lấy lịch sử giao dịch
     */
    List<WalletTransactionResponseDTO> getTransactionHistory(User user);

    /**
     * Xử lý thanh toán đơn hàng bằng ví (internal use)
     * Trừ tiền người mua, cộng tiền người bán
     */
    void processOrderPayment(Order order);

    /**
     * Hoàn tiền khi hủy đơn hàng
     */
    void refundOrder(Order order);
}

