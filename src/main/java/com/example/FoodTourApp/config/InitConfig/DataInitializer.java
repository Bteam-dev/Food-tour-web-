package com.example.FoodTourApp.config.InitConfig;

import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.RoleRepository;
import com.example.FoodTourApp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RoleRepository roleRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Initializing roles and admin user...");

        // Initialize roles
        if (roleRepository.count() == 0) {
            logger.info("No roles found, creating roles...");

            Role adminRole = new Role();
            adminRole.setRoleName(Role.RoleName.ADMIN);
            adminRole.setDescription("Administrator role");
            adminRole.setCreatedAt(LocalDateTime.now());
            roleRepository.save(adminRole);
            logger.info("Created role: admin");

            Role userRole = new Role();
            userRole.setRoleName(Role.RoleName.USER);
            userRole.setDescription("User role");
            userRole.setCreatedAt(LocalDateTime.now());
            roleRepository.save(userRole);
            logger.info("Created role: user");

            Role sellerRole = new Role();
            sellerRole.setRoleName(Role.RoleName.SELLER);
            sellerRole.setDescription("Seller role");
            sellerRole.setCreatedAt(LocalDateTime.now());
            roleRepository.save(sellerRole);
            logger.info("Created role: seller");
        } else {
            logger.info("Roles already exist, skipping role creation.");
        }

        // Initialize admin user
        if (userRepository.findByEmail("pheuconbattai@gmail.com").isEmpty()) {
            logger.info("No admin user found, creating admin user...");
            User admin = new User();
            admin.setUsername("pheuconbattai196");
            admin.setEmail("pheuconbattai@gmail.com");
            admin.setPasswordHash(passwordEncoder.encode("Minhngosen196@"));
            admin.setFullName("Admin Tổng");
            admin.setPhone("1234567890");
            admin.setAvatarUrl(null); // Admin không cần avatar lúc khởi tạo
            admin.setDateOfBirth(null); // Có thể cập nhật sau
            admin.setGender(null); // Có thể cập nhật sau
            Role adminRole = roleRepository.findByRoleName(Role.RoleName.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("Admin role not found"));
            admin.setRole(adminRole);
            admin.setIsActive(true);
            admin.setEmailVerified(true);
            admin.setWalletBalance(new java.math.BigDecimal("0.00")); // Ví admin bắt đầu từ 0, sẽ nhận hoa hồng 12% từ mỗi đơn
            admin.setLastLogin(null); // Chưa đăng nhập lần nào
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());
            userRepository.save(admin);
            logger.info("Created admin user with email: admin@foodtourapp.com (Wallet: 0 VND - will receive 12% commission from orders)");
        } else {
            logger.info("Admin user already exists, skipping admin user creation.");
        }
    }
}
