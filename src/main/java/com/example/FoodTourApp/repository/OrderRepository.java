package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {
    // WITH PAGINATION
    Page<Order> findByUser(User user, Pageable pageable);
}
