package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    List<Order> findByUser(User user);
}
