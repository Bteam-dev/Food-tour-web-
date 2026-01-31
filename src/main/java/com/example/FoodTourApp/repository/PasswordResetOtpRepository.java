package com.example.FoodTourApp.repository;

import com.example.FoodTourApp.entity.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Integer> {

    // Tìm OTP theo email và code (chưa sử dụng)
    Optional<PasswordResetOtp> findByEmailAndOtpCodeAndIsUsedFalse(String email, String otpCode);

    // Xóa các OTP cũ của email (để tránh spam khi gửi OTP mới)
    void deleteByEmail(String email);
}
