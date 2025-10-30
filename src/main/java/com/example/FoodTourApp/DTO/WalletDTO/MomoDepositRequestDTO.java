package com.example.FoodTourApp.DTO.WalletDTO;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Getter
@Setter
public class MomoDepositRequestDTO {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "10000", message = "Minimum deposit amount is 10,000 VND")
    private BigDecimal amount;

    private String description;

    // URL để redirect sau khi thanh toán thành công/thất bại
    private String returnUrl;

    // Phương thức thanh toán: "app" (dùng App MOMO) hoặc "card" (dùng thẻ test)
    // Mặc định là "app" để có thể test bằng tài khoản MOMO UAT trên điện thoại
    private String paymentMethod = "app";
}