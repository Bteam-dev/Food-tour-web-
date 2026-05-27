package com.example.FoodTourApp.DTO.VoucherDTO;

import lombok.Data;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateVoucherRequest {

    @NotBlank(message = "Mã voucher không được để trống")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Tiêu đề không được để trống")
    private String title;

    private String description;

    /** PLATFORM hoặc SHOP */
    @NotBlank
    private String scope;

    /** PERCENT, FIXED, FREE_SHIP */
    @NotBlank
    private String discountType;

    @NotNull
    @DecimalMin("0")
    private BigDecimal discountValue;

    /** Chỉ dùng cho PERCENT: giới hạn tiền giảm tối đa */
    private BigDecimal maxDiscountAmount;

    /** Giá trị đơn hàng tối thiểu */
    private BigDecimal minOrderValue;

    /** Tổng lượt dùng tối đa (null = không giới hạn) */
    private Integer maxUsage;

    /** Mỗi user tối đa dùng bao nhiêu lần */
    private Integer maxUsagePerUser;

    @NotNull
    private LocalDateTime startDate;

    @NotNull
    private LocalDateTime endDate;

    /** shopId - chỉ điền khi scope = SHOP */
    private Integer shopId;
}

