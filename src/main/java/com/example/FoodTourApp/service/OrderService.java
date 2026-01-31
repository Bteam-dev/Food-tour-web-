package com.example.FoodTourApp.service;


import com.example.FoodTourApp.DTO.OrderDTO.CreateOrderRequestDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponseDTO createOrder(CreateOrderRequestDTO request, User user);

    // WITH PAGINATION
    Page<OrderResponseDTO> getUserOrders(User user, Pageable pageable);

    OrderResponseDTO getOrderById(Integer orderId, User user);
    void deleteOrder(Integer orderId, User user);
    OrderResponseDTO payOrder(Integer orderId, User user);
}
