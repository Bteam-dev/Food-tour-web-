package com.example.FoodTourApp.DTO.OrderDTO;

import com.example.FoodTourApp.DTO.ShopDTO.AddressResponseDTO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class OrderResponseDTO {

    private Integer id;
    private String orderNumber;
    private Integer userId;
    private String userName;
    private Integer shopId;
    private String shopName;
    private AddressResponseDTO deliveryAddress;
    private String orderStatus;
    private String paymentStatus;
    private String paymentMethod;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private BigDecimal platformCommissionRate;
    private BigDecimal platformCommissionAmount;
    private BigDecimal sellerReceivedAmount;
    private String notes;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime actualDeliveryTime;
    private String cancelledReason;
    private LocalDateTime cancelledAt;
    private Integer cancelledById;
    private String cancelledByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // YÊU CẦU HOÀN TIỀN - Đơn hàng có review yêu cầu refund
    private Boolean hasRefundRequest;

    /** Khoảng cách giao hàng (km) */
    private BigDecimal deliveryDistanceKm;

    /** Voucher đã áp dụng */
    private String voucherCode;

    private List<OrderItemResponseDTO> orderItems;
}
