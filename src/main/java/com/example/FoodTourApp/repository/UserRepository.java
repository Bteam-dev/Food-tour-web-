package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(String phone);

    // Tìm admin account đầu tiên để nhận hoa hồng nền tảng
    @Query("SELECT u FROM User u WHERE u.role.roleName = :roleName ORDER BY u.id ASC")
    Optional<User> findFirstByRoleName(@Param("roleName") Role.RoleName roleName);
}