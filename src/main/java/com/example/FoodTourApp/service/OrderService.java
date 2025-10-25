package com.example.FoodTourApp.service;


import com.example.FoodTourApp.DTO.OrderDTO.CreateOrderRequestDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.entity.User;

import java.util.List;

public interface OrderService {

    OrderResponseDTO createOrder(CreateOrderRequestDTO request, User user);
    List<OrderResponseDTO> getUserOrders(User user);
    void deleteOrder(Integer orderId, User user);
}
