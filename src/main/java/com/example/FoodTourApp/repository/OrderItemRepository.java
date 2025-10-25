package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.OrderItem;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Integer> {
    List<OrderItem> findByOrder(Order order);
}
