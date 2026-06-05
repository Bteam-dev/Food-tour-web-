package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    Optional<User> findByPhone(String phone);
    Optional<User> findByGoogleId(String googleId);

    // Tìm admin account đầu tiên để nhận hoa hồng nền tảng
    @Query("SELECT u FROM User u WHERE u.role.roleName = :roleName ORDER BY u.id ASC")
    Optional<User> findFirstByRoleName(@Param("roleName") Role.RoleName roleName);

    // Tìm kiếm theo tên (fullName hoặc username) và/hoặc role
    @Query("SELECT u FROM User u WHERE " +
           "(:name IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :name, '%')) OR LOWER(u.username) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:roleName IS NULL OR u.role.roleName = :roleName)")
    List<User> searchByNameAndRole(@Param("name") String name, @Param("roleName") Role.RoleName roleName);
}