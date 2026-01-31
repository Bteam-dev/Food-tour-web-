package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.WalletDTO.WalletResponseDTO;
import com.example.FoodTourApp.DTO.WalletDTO.WalletTransactionResponseDTO;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalletService {

    /**
     * Lấy thông tin ví của user
     */
    WalletResponseDTO getWalletInfo(User user);

    /**
     * Lấy lịch sử giao dịch - WITH PAGINATION
     */
    Page<WalletTransactionResponseDTO> getTransactionHistory(User user, Pageable pageable);

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
