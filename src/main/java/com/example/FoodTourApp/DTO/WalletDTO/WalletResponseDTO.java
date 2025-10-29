package com.example.FoodTourApp.DTO.WalletDTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponseDTO {
    private Integer userId;
    private String fullName;

    private BigDecimal balance;
    private List<WalletTransactionResponseDTO> recentTransactions;

    // Thêm field để trả về khi nạp tiền
    private String paymentUrl;  // Chỉ có khi gọi initiateDeposit
    private String message;    // Thông báo thành công/thất bại
}

