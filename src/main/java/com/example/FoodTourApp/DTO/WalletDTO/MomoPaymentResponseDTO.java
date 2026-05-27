package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MomoPaymentResponseDTO {

    private String payUrl;          // URL để redirect user đến trang thanh toán MOMO
    private String orderId;         // Mã đơn hàng nạp tiền
    private String requestId;       // Request ID từ MOMO
    private Long amount;            // Số tiền
    private String message;         // Thông báo
    private Integer resultCode;     // Mã kết quả từ MOMO
}

