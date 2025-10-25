package com.example.FoodTourApp.DTO.OrderDTO;

import com.example.FoodTourApp.DTO.ShopDTO.AddressResponseDTO;
import lombok.Getter;
import lombok.Setter;

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
    private Double subtotal;
    private Double deliveryFee;
    private Double discountAmount;
    private Double taxAmount;
    private Double totalAmount;
    private String notes;
    private LocalDateTime estimatedDeliveryTime;
    private LocalDateTime actualDeliveryTime;
    private String cancelledReason;
    private LocalDateTime cancelledAt;
    private Integer cancelledById;
    private String cancelledByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemResponseDTO> orderItems;
}
